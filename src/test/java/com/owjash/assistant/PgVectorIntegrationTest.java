package com.owjash.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

// Run only against the disposable postgres-test service described in SETUP.md.
@EnabledIfEnvironmentVariable(named="RUN_PGVECTOR_TESTS",matches="true")
@SpringBootTest(properties={"app.seed-demo=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:postgresql://localhost:5433/smart_inventory_test"})
@ActiveProfiles("postgres")
class PgVectorIntegrationTest {
    @Autowired PgVectorIndex pg;
    @Autowired Passages passages;
    @Test void pgvectorUsesCosineAndKeepsDocumentScope() {
        String a=UUID.randomUUID().toString(), b=UUID.randomUUID().toString();
        Passage one=new Passage(UUID.randomUUID().toString(),a,2,"A supported policy passage.");one.vectorJson="[1,0,0]";one.embeddingModel="test";
        Passage other=new Passage(UUID.randomUUID().toString(),b,8,"Must not cross document boundaries.");other.vectorJson="[1,0,0]";other.embeddingModel="test";
        passages.save(one);passages.save(other);pg.upsert(one);pg.upsert(other);
        var found=pg.search(a,"test",new float[]{1,0,0});
        assertThat(found).hasSize(1);assertThat(found.getFirst().page()).isEqualTo(2);assertThat(found.getFirst().text()).contains("supported policy");
        pg.delete(a);assertThat(pg.search(a,"test",new float[]{1,0,0})).isEmpty();pg.delete(b);
    }
}
