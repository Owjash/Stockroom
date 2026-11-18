package com.owjash.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnswerGuardTest {
    private static final String INTERNAL_ID = "11111111-2222-3333-4444-555555555555";
    private final ApiTypes.Source source = new ApiTypes.Source(
        INTERNAL_ID, 2, "Unopened products may be returned within 30 calendar days of delivery.", 0.8);
    private static final String DRAFT = "{\"answer\":\"30 calendar days\",\"sourceIds\":[\"S1\"],\"insufficient\":false}";
    private static final String SUPPORTED = "{\"answersQuestion\":true,\"fullySupported\":true,\"evidence\":[{\"sourceId\":\"S1\",\"quote\":\"Unopened products may be returned within 30 calendar days of delivery.\"}]}";

    private AnswerService service(String draft, String verdict) {
        return service(draft, verdict, new ArrayList<>());
    }

    private AnswerService service(String draft, String verdict, List<Prompt> prompts) {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            return response(prompts.size() == 1 ? draft : verdict);
        });
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("model", model);
        return new AnswerService(null, beans.getBeanProvider(ChatModel.class), true);
    }

    private ChatResponse response(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    @Test void acceptsSupportedAnswerWithExactEvidence() {
        var result = service(DRAFT, SUPPORTED).respond(
            "Within how many days may unopened products be returned?", List.of(source), 0);
        assertThat(result.insufficient()).isFalse();
        assertThat(result.sources()).extracting(ApiTypes.Source::id).containsExactly(INTERNAL_ID);
    }

    @Test void inventedCitationCausesAbstention() {
        String draft = DRAFT.replace("S1", "invented-id");
        assertThat(service(draft, SUPPORTED).respond("question", List.of(source), 0).insufficient()).isTrue();
    }

    @Test void noSourcesCausesAbstentionBeforeGeneration() {
        assertThat(service("unused", "unused").respond("question", List.of(), 0).insufficient()).isTrue();
    }

    @Test void rejectsAnAnswerAboutADifferentAttribute() {
        String verdict = "{\"answersQuestion\":true,\"fullySupported\":false,\"evidence\":[]}";
        assertThat(service(DRAFT, verdict).respond("What is the warranty duration?", List.of(source), 0).insufficient()).isTrue();
    }

    @Test void rejectsAnAnswerThatOnlyRepeatsTheQuestionTopic() {
        String verdict = "{\"answersQuestion\":false,\"fullySupported\":false,\"evidence\":[]}";
        String draft = DRAFT.replace("30 calendar days", "bulk purchases");
        assertThat(service(draft, verdict).respond("What discount applies to bulk purchases?", List.of(source), 0).insufficient()).isTrue();
    }

    @Test void rejectsFabricatedSupportingQuotes() {
        String verdict = SUPPORTED.replace("Unopened products may be returned within 30 calendar days of delivery.", "A warranty lasts 30 calendar days.");
        assertThat(service(DRAFT, verdict).respond("What is the warranty duration?", List.of(source), 0).insufficient()).isTrue();
    }

    @Test void rejectsMissingEvidenceEvenWithPositiveVerdict() {
        String verdict = "{\"answersQuestion\":true,\"fullySupported\":true,\"evidence\":[]}";
        assertThat(service(DRAFT, verdict).respond("question", List.of(source), 0).insufficient()).isTrue();
    }

    @Test void invalidVerifierResponseIsAnErrorNotSuccessfulAbstention() {
        assertThatThrownBy(() -> service(DRAFT, "{}").respond("question", List.of(source), 0))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(502));
    }

    @Test void mapsSecondRetrievedPassageBackToItsRealId() {
        var first = new ApiTypes.Source("99999999-aaaa-bbbb-cccc-dddddddddddd", 1,
            "The minimum order quantity is 50 units per product SKU.", 0.9);
        var result = service(DRAFT.replace("S1", "S2"), SUPPORTED).respond(
            "What is the return window?", List.of(first, source), 0);
        assertThat(result.insufficient()).isFalse();
        assertThat(result.sources()).extracting(ApiTypes.Source::id).containsExactly(INTERNAL_ID);
        assertThat(result.sources()).extracting(ApiTypes.Source::page).containsExactly(2);
    }

    @Test void rejectsUnknownVerifierLabelsEvenWithPositiveVerdict() {
        var result = service(DRAFT, SUPPORTED.replace("S1", "S99"))
            .respond("question", List.of(source), 0);
        assertThat(result.insufficient()).isTrue();
    }

    @Test void rejectsTruncatedDatabaseIdsRatherThanGuessing() {
        String truncated = INTERNAL_ID.replace("-2222", "");
        var result = service(DRAFT.replace("S1", truncated), SUPPORTED)
            .respond("question", List.of(source), 0);
        assertThat(result.insufficient()).isTrue();
    }
//done
    @Test void bothModelRequestsUseShortLabelsWithoutInternalIds() {
        List<Prompt> prompts = new ArrayList<>();
        var result = service(DRAFT, SUPPORTED, prompts)
            .respond("What is the return window?", List.of(source), 0);
        assertThat(result.insufficient()).isFalse();
        assertThat(prompts).hasSize(2);
        for (Prompt prompt : prompts) {
            assertThat(prompt.toString()).contains("PASSAGE ID: S1").doesNotContain(INTERNAL_ID);
        }
    }
}
