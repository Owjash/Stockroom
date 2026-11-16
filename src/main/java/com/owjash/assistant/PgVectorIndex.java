package com.owjash.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.owjash.assistant.ApiTypes.*;

@Component
public class PgVectorIndex {
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    PgVectorIndex(JdbcTemplate jdbc,@Value("${app.retrieval-engine:memory}") String engine) {
        this.jdbc=jdbc; enabled="pgvector".equals(engine);
    }
    public boolean enabled() { return enabled; }
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (!enabled) return;
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("CREATE TABLE IF NOT EXISTS passage_vector (id varchar(255) PRIMARY KEY, document_id varchar(255) NOT NULL, embedding_model varchar(255) NOT NULL, embedding vector NOT NULL)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS passage_vector_document_idx ON passage_vector(document_id)");
    }
    public void upsert(Passage passage) {
        if (!enabled) return;
        jdbc.update("INSERT INTO passage_vector(id,document_id,embedding_model,embedding) VALUES (?,?,?,?::vector) ON CONFLICT (id) DO UPDATE SET embedding=EXCLUDED.embedding,embedding_model=EXCLUDED.embedding_model",
                passage.id,passage.documentId,passage.embeddingModel,passage.vectorJson);
    }
    public List<Source> search(String documentId,String model,float[] vector) {
        // Parameterized exact cosine search. Top 3 is computed in PostgreSQL, not in Java.
        String literal=Arrays.toString(vector);
        return jdbc.query("""
            SELECT p.id, p.page_number, p.content, 1-(v.embedding <=> ?::vector) AS score
            FROM passage_vector v JOIN passage p ON p.id=v.id
            WHERE v.document_id=? AND v.embedding_model=? AND 1-(v.embedding <=> ?::vector)>=0.42
            ORDER BY v.embedding <=> ?::vector, p.page_number, p.id LIMIT 3
            """,(row,n) -> new Source(row.getString("id"),row.getInt("page_number"),row.getString("content"),row.getDouble("score")),
                literal,documentId,model,literal,literal);
    }
    public void delete(String documentId) {
        if (enabled) jdbc.update("DELETE FROM passage_vector WHERE document_id=?",documentId);
    }
}
