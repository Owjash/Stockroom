package com.owjash.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.owjash.assistant.ApiTypes.Answer;
import com.owjash.assistant.ApiTypes.EvalCase;
import com.owjash.assistant.ApiTypes.EvalRow;
import com.owjash.assistant.ApiTypes.Evaluation;
import com.owjash.assistant.ApiTypes.Source;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import static com.owjash.assistant.ApiTypes.*;

@Service
@Transactional
public class DocumentService {
    private final Documents documents;
    private final Passages passages;
    private final Questions questions;
    private final PdfIngestor ingestor;
    private final Retriever retriever;
    private final AnswerService answers;
    private final ObjectMapper json;
    private final PgVectorIndex pg;

    DocumentService(Documents documents, Passages passages, Questions questions, PdfIngestor ingestor,
                    Retriever retriever, AnswerService answers, ObjectMapper json, PgVectorIndex pg) {
        this.documents=documents; this.passages=passages; this.questions=questions;
        this.ingestor=ingestor; this.retriever=retriever; this.answers=answers; this.json=json;
        this.pg=pg;
    }
    public List<DocumentRecord> list() { return documents.findAllByOrderByCreatedAtDesc(); }
    public DocumentRecord get(String id) {
        return documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Document not found."));
    }
    @Transactional
    public DocumentRecord upload(String filename,byte[] bytes) {
        if (documents.count() >= 10) throw PdfIngestor.invalid("This local workspace supports 10 documents. Delete one before adding another.");
        String name=filename==null?"document.pdf":filename.replace('\\','/');
        name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");
        if (name.isBlank()) name="document.pdf";
        if (name.length()>150) name=name.substring(0,146)+".pdf";
        String id=UUID.randomUUID().toString();
        var parsed=ingestor.parse(bytes,id);
        var doc=new DocumentRecord(id,name,parsed.pages(),parsed.passages().size(),bytes.length);
        doc.pdfBytes=bytes;
        documents.save(doc); passages.saveAll(parsed.passages());
        retriever.index(parsed.passages());
        return doc;
    }
    @Transactional
    public void delete(String id) {
        get(id); questions.deleteByDocumentId(id); pg.delete(id); passages.deleteByDocumentId(id); documents.deleteById(id);
    }
    public Answer ask(String id, String question) {
        get(id); var answer=answers.ask(id,question.trim());
        try { questions.save(new QuestionRecord(UUID.randomUUID().toString(),id,question.trim(),json.writeValueAsString(answer))); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not save answer",e); }
        return answer;
    }
    public List<Answer> history(String id) {
        get(id);
        return questions.findTop30ByDocumentIdOrderByCreatedAtDesc(id).reversed().stream().map(record -> {
            try { return json.readValue(record.responseJson,Answer.class); }
            catch (JsonProcessingException e) { throw new IllegalStateException("Could not read history",e); }
        }).toList();
    }
    public Evaluation evaluate(String id,List<EvalCase> cases,boolean endToEnd) {
        if (endToEnd && !answers.generativeEnabled()) throw PdfIngestor.invalid("End-to-end evaluation requires the ai profile. Disable it for retrieval-only evaluation.");
        var doc=get(id); var rows=new ArrayList<EvalRow>();
        for (EvalCase c : cases) {
            if (c.unanswerable() && c.expectedPage()!=null) throw PdfIngestor.invalid("Unanswerable cases must have expectedPage=null.");
            if (!c.unanswerable() && (c.expectedPage()==null || c.expectedPage()>doc.pageCount))
                throw PdfIngestor.invalid("Answerable cases need a valid expectedPage in this document.");
            long start=System.nanoTime();
            var sources=retriever.search(id,c.question());
            long ms=(System.nanoTime()-start)/1_000_000;
            var pages=sources.stream().map(Source::page).distinct().toList();
            boolean abstained=endToEnd?answers.respond(c.question(),sources,ms).insufficient():sources.isEmpty();
            boolean pass = c.unanswerable()
                 ? abstained
                 : pages.contains(c.expectedPage()) && !abstained;
            rows.add(new EvalRow(c.question(),c.expectedPage(),c.unanswerable(),abstained,pages,pass,ms));
        }
        int positive=(int)rows.stream().filter(r -> !r.unanswerable()).count();
        int negative=rows.size()-positive;
        long positivePass = rows.stream()
            .filter(r -> !r.unanswerable()
             && r.retrievedPages().contains(r.expectedPage()))
                .count();
        long negativePass=rows.stream().filter(r -> r.unanswerable() && r.passed()).count();
        var times=rows.stream().mapToLong(EvalRow::retrievalMs).sorted().toArray();
        double median=times.length%2==0?(times[times.length/2-1]+times[times.length/2])/2.0:times[times.length/2];
        return new Evaluation(retriever.mode(),rows.size(),positive,negative,
                positive==0?null:(double)positivePass/positive,
                negative==0?null:(double)negativePass/negative,
                rows.stream().mapToLong(EvalRow::retrievalMs).average().orElse(0),median,times[(int)Math.ceil(times.length*.95)-1],
                endToEnd?"model decision":"retrieval threshold only",1,doc.pageCount,doc.chunkCount,java.time.Instant.now().toString(),rows);
    }
}
