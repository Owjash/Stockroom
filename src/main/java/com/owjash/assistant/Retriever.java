package com.owjash.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.regex.Pattern;
import static com.owjash.assistant.ApiTypes.*;

@Service
public class Retriever {
    private final Passages repository;
    private final ObjectMapper json;
    private final EmbeddingModel model;
    private final String modelName;
    private final boolean aiEnabled;
    private final PgVectorIndex pg;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:[.][\\p{N}]+)?");
    private static final Set<String> STOP = Set.of("a","an","the","of","to","for","in","on","and","or","is","are","was","what","which","how","does","do","can","it","its","this","that","be","with","please","tell","me","about");

    Retriever(Passages repository, ObjectMapper json, ObjectProvider<EmbeddingModel> provider,
              @Value("${app.embedding-model}") String modelName, @Value("${app.ai-enabled:false}") boolean enabled, PgVectorIndex pg) {
        this.repository=repository; this.json=json; this.model=provider.getIfAvailable(); this.modelName=modelName; aiEnabled=enabled;
        this.pg=pg;
    }
    public String mode() { return aiEnabled ? "semantic" : "keyword"; }

    // Cache vectors in H2 for this small, local corpus. Upgrade to pgvector for larger collections.
    public void index(List<Passage> passages) {
        if (!aiEnabled) return;
        if (model == null) throw unavailable();
        try {
            for (Passage p : passages) {
                if (p.vectorJson == null || !modelName.equals(p.embeddingModel)) {
                    p.vectorJson=json.writeValueAsString(model.embed(p.content));
                    p.embeddingModel=modelName;
                    repository.save(p);
                    pg.upsert(p);
                }
            }
        } catch (Exception e) { throw unavailable(); }
    }

    public List<Source> search(String documentId, String question) {
        List<Passage> passages=repository.findByDocumentIdOrderByPageNumberAscIdAsc(documentId);
        float[] queryVector=null;
        if (aiEnabled) {
            index(passages);
            try { queryVector=model.embed(question); }
            catch (Exception e) { throw unavailable(); }
        }
        if (aiEnabled && pg.enabled()) return pg.search(documentId,modelName,queryVector);
        Set<String> query=tokens(question);
        var scored=new ArrayList<Source>();
        for (Passage p : passages) {
            double score;
            if (aiEnabled) {
                try { score=cosine(queryVector, json.readValue(p.vectorJson, float[].class)); }
                catch (JsonProcessingException e) { throw unavailable(); }
            } else score=lexicalScore(query,p.content);
            // These heuristic cutoffs need calibration on the user's own evaluation set.
            if (score >= (aiEnabled ? 0.42 : 0.26))
                scored.add(new Source(p.id,p.pageNumber,p.content,Math.round(score*1000)/1000.0));
        }
        return scored.stream().sorted(Comparator.comparingDouble(Source::score).reversed()
                .thenComparingInt(Source::page).thenComparing(Source::id)).limit(3).toList();
    }
    static Set<String> tokens(String text) {
        Set<String> result=new HashSet<>(); var matcher=WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) { String word=matcher.group(); if (!STOP.contains(word)) result.add(word); }
        return result;
    }
    static double lexicalScore(Set<String> query, String content) {
        if (query.isEmpty()) return 0;
        Set<String> words=tokens(content); long hits=query.stream().filter(words::contains).count();
        return (double)hits/query.size();
    }
    static double cosine(float[] a, float[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("Embedding dimensions differ");
        double dot=0, aa=0, bb=0;
        for (int i=0;i<a.length;i++) { dot+=a[i]*b[i]; aa+=a[i]*a[i]; bb+=b[i]*b[i]; }
        return aa==0 || bb==0 ? 0 : dot/Math.sqrt(aa*bb);
    }
    static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"The embedding model is unavailable. Check Ollama and the configured model, or restart without the ai profile.");
    }
}
