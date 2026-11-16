package com.owjash.assistant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RetrievalMathTest {
    @Test void cosineHandlesDirectionsAndEmptyVectors() {
        assertThat(Retriever.cosine(new float[]{1,0},new float[]{1,0})).isEqualTo(1);
        assertThat(Retriever.cosine(new float[]{1,0},new float[]{0,1})).isZero();
        assertThat(Retriever.cosine(new float[]{0,0},new float[]{1,0})).isZero();
        assertThatThrownBy(() -> Retriever.cosine(new float[]{1},new float[]{1,2})).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void chunkingPreservesPageContentWithOverlapAndBoundedChunks() {
        String text="sensor voltage reference ".repeat(140); var parts=PdfIngestor.split(text);
        assertThat(parts).hasSizeGreaterThan(1);assertThat(parts).allMatch(p->p.length()<=1200);
        assertThat(parts.get(0)).contains(parts.get(1).substring(0,100));
    }
}
