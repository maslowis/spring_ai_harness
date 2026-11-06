package org.maslowis.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
//@EnabledIfSystemProperty(named = "spring.profiles.active", matches = "integration-test")
public class AgentOllamaTest {

    @Autowired
    Agent agent;

    @Test
    void testOllamaAnswer() {
        String answer = agent.run("Reply with exactly one short sentence about yourself.");
        assertThat(answer).isNotBlank();
        // Если think-option: false не подхватился, Ollama вернёт рассуждения модели
        // в отдельном поле, а некоторые сборки — прямо в тексте тегами <think>.
        assertThat(answer).doesNotContain("<think>");
    }
}
