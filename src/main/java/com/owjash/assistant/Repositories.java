package com.owjash.assistant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface Documents extends JpaRepository<DocumentRecord,String> {
    List<DocumentRecord> findAllByOrderByCreatedAtDesc();
}
interface Passages extends JpaRepository<Passage,String> {
    List<Passage> findByDocumentIdOrderByPageNumberAscIdAsc(String documentId);
    void deleteByDocumentId(String documentId);
}
interface Questions extends JpaRepository<QuestionRecord,String> {
    List<QuestionRecord> findTop30ByDocumentIdOrderByCreatedAtDesc(String documentId);
    void deleteByDocumentId(String documentId);
}
