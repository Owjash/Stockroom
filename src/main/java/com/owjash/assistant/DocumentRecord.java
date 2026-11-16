package com.owjash.assistant;

import jakarta.persistence.*;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class DocumentRecord {
    @Id public String id;
    public String name;
    public int pageCount;
    public int chunkCount;
    public long byteCount;
    public Instant createdAt;
    @Lob @JsonIgnore public byte[] pdfBytes;
    protected DocumentRecord() {}
    public DocumentRecord(String id, String name, int pages, int chunks, long bytes) {
        this.id = id; this.name = name; pageCount = pages; chunkCount = chunks;
        byteCount = bytes; createdAt = Instant.now();
    }
}
