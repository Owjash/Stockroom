package com.owjash.assistant;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class QuestionRecord {
    @Id public String id;
    public String documentId;
    @Column(length=1000) public String question;
    @Column(columnDefinition="text") public String responseJson;
    public Instant createdAt;
    protected QuestionRecord() {}
    public QuestionRecord(String id, String doc, String question, String response) {
        this.id=id; documentId=doc; this.question=question; responseJson=response; createdAt=Instant.now();
    }
}
