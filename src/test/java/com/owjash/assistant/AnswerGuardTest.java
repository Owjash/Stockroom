package com.owjash.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnswerGuardTest {
    private final ApiTypes.Source source=new ApiTypes.Source("verified-id",2,"Returns require authorization.",0.8);
    private AnswerService service(String result,List<ApiTypes.Source> sources) {
        Retriever retriever=mock(Retriever.class);when(retriever.search("doc","question")).thenReturn(sources);
        ChatModel model=mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(result)))));
        var beans=new DefaultListableBeanFactory();beans.registerSingleton("model",model);
        return new AnswerService(retriever,beans.getBeanProvider(ChatModel.class),true);
    }
    @Test void acceptsOnlyCitationsFromRetrievedPassages() {
        var result=service("{\"answer\":\"Returns require authorization.\",\"sourceIds\":[\"verified-id\"],\"insufficient\":false}",List.of(source)).ask("doc","question");
        assertThat(result.insufficient()).isFalse();assertThat(result.sources()).extracting(ApiTypes.Source::id).containsExactly("verified-id");
    }
    @Test void inventedCitationCausesAbstention() {
        var result=service("{\"answer\":\"Unsupported claim\",\"sourceIds\":[\"invented-id\"],\"insufficient\":false}",List.of(source)).ask("doc","question");
        assertThat(result.insufficient()).isTrue();assertThat(result.sources()).isEmpty();
    }
    @Test void noSourcesCausesAbstentionBeforeGeneration() {
        assertThat(service("unused",List.of()).ask("doc","question").insufficient()).isTrue();
    }
}
