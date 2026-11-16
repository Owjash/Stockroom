package com.owjash.assistant;

import jakarta.validation.constraints.*;
import java.util.List;

public final class ApiTypes {
    private ApiTypes() {}
    public record AskRequest(@NotBlank @Size(max=1000) String question) {}
    public record Source(String id, int page, String text, double score) {}
    public record Answer(String question, String answer, String mode, boolean insufficient,
                         List<Source> sources, long retrievalMs, long generationMs) {}
    public record Draft(String answer, List<String> sourceIds, boolean insufficient) {}
    public record EvalCase(@NotBlank @Size(max=1000) String question,
                           @Min(1) Integer expectedPage, boolean unanswerable) {}
    public record EvalRequest(@NotEmpty @Size(max=50) List<@NotNull @jakarta.validation.Valid EvalCase> cases, boolean endToEnd) {}
    public record EvalRow(String question, Integer expectedPage, boolean unanswerable,
                          boolean abstained, List<Integer> retrievedPages, boolean passed, long retrievalMs) {}
    public record Evaluation(String mode, int count, int positiveCases, int negativeCases,
                              Double pageHitRateAt3, Double abstentionAccuracy,
                              double meanRetrievalMs, double medianRetrievalMs, long p95RetrievalMs,
                              String abstentionBasis, int documents, int pages, int chunks,
                              String generatedAt, List<EvalRow> rows) {}
}
