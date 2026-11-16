package com.owjash.assistant;

import jakarta.persistence.*;

@Entity
public class Passage {
    @Id public String id;
    @Column(nullable=false) public String documentId;
    public int pageNumber;
    @Column(columnDefinition="text") public String content;
    @Column(columnDefinition="text") public String vectorJson;
    public String embeddingModel;
    protected Passage() {}
    public Passage(String id, String documentId, int page, String content) {
        this.id = id; this.documentId = documentId; pageNumber = page; this.content = content;
    }
}
