package com.owjash.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnswerGuardTest {

    private final ApiTypes.Source source = new ApiTypes.Source(
        "verified-id",
        2,
        "Unopened products may be returned within 30 calendar days of delivery.",
        0.8
    );

    private static final String DRAFT =
        "{\"answer\":\"30 calendar days\","
        + "\"sourceIds\":[\"verified-id\"],"
        + "\"insufficient\":false}";

    private static final String SUPPORTED =
        "{\"answersQuestion\":true,"
        + "\"fullySupported\":true,"
        + "\"evidence\":[{"
        + "\"sourceId\":\"verified-id\","
        + "\"quote\":\"Unopened products may be returned within 30 calendar days of delivery.\""
        + "}]}";

    private AnswerService service(String draft, String verdict) {
        ChatModel model = mock(ChatModel.class);

        when(model.call(any(Prompt.class)))
            .thenReturn(response(draft), response(verdict));

        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("model", model);

        return new AnswerService(
            null,
            beans.getBeanProvider(ChatModel.class),
            true
        );
    }

    private ChatResponse response(String json) {
        return new ChatResponse(
            List.of(new Generation(new AssistantMessage(json)))
        );
    }

    @Test
    void acceptsSupportedAnswerWithExactEvidence() {
        var result = service(DRAFT, SUPPORTED).respond(
            "Within how many days may unopened products be returned?",
            List.of(source),
            0
        );

        assertThat(result.insufficient()).isFalse();
        assertThat(result.sources())
            .extracting(ApiTypes.Source::id)
            .containsExactly("verified-id");
    }

    @Test
    void inventedCitationCausesAbstention() {
        String draft = DRAFT.replace("verified-id", "invented-id");

        var result = service(draft, SUPPORTED)
            .respond("question", List.of(source), 0);

        assertThat(result.insufficient()).isTrue();
    }

    @Test
    void noSourcesCausesAbstentionBeforeGeneration() {
        var result = service("unused", "unused")
            .respond("question", List.of(), 0);

        assertThat(result.insufficient()).isTrue();
    }

    @Test
    void rejectsAnAnswerAboutADifferentAttribute() {
        String verdict =
            "{\"answersQuestion\":true,"
            + "\"fullySupported\":false,"
            + "\"evidence\":[]}";

        var result = service(DRAFT, verdict).respond(
            "What is the warranty duration?",
            List.of(source),
            0
        );

        assertThat(result.insufficient()).isTrue();
    }

    @Test
    void rejectsAnAnswerThatOnlyRepeatsTheQuestionTopic() {
        String verdict =
            "{\"answersQuestion\":false,"
            + "\"fullySupported\":false,"
            + "\"evidence\":[]}";

        String draft = DRAFT.replace(
            "30 calendar days",
            "bulk purchases"
        );

        var result = service(draft, verdict).respond(
            "What discount applies to bulk purchases?",
            List.of(source),
            0
        );

        assertThat(result.insufficient()).isTrue();
    }

    @Test
    void rejectsFabricatedSupportingQuotes() {
        String verdict = SUPPORTED.replace(
            "Unopened products may be returned within 30 calendar days of delivery.",
            "A warranty lasts 30 calendar days."
        );

        var result = service(DRAFT, verdict).respond(
            "What is the warranty duration?",
            List.of(source),
            0
        );

        assertThat(result.insufficient()).isTrue();
    }

    @Test
    void rejectsMissingEvidenceEvenWithPositiveVerdict() {
        String verdict =
            "{\"answersQuestion\":true,"
            + "\"fullySupported\":true,"
            + "\"evidence\":[]}";

        var result = service(DRAFT, verdict)
            .respond("question", List.of(source), 0);

        assertThat(result.insufficient()).isTrue();
    }

    @Test
    void invalidVerifierResponseIsAnErrorNotSuccessfulAbstention() {
        assertThatThrownBy(() ->
            service(DRAFT, "{}")
                .respond("question", List.of(source), 0)
        )
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error ->
                assertThat(
                    ((ResponseStatusException) error)
                        .getStatusCode()
                        .value()
                ).isEqualTo(502)
            );
    }
}