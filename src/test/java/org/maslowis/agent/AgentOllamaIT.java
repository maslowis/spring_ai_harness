package org.maslowis.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfSystemProperty(named = "spring.profiles.active", matches = "IT")
@Execution(ExecutionMode.SAME_THREAD)
public class AgentOllamaIT {

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

    @Test
    void testOllamaReadFile() {
        String answer = agent.run("What Java version is used in the project?");
        assertThat(answer).isNotBlank();
        // Если think-option: false не подхватился, Ollama вернёт рассуждения модели
        // в отдельном поле, а некоторые сборки — прямо в тексте тегами <think>.
        assertThat(answer).doesNotContain("<think>");
    }

    @Test
    void testOllamaAnswerFindClass() {
        String answer = agent.run("In which file is the class LLMToolExecutor declared? Give the path and explain what this class does.");
        assertThat(answer).isNotBlank();
        // Если think-option: false не подхватился, Ollama вернёт рассуждения модели
        // в отдельном поле, а некоторые сборки — прямо в тексте тегами <think>.
        assertThat(answer).doesNotContain("<think>");
    }

    @Test
    void testOllamaAnswerFindCode() {
        String answer = agent.run("And who listens to ToolCallEnd? Find the file.");
        assertThat(answer).isNotBlank();
        // Если think-option: false не подхватился, Ollama вернёт рассуждения модели
        // в отдельном поле, а некоторые сборки — прямо в тексте тегами <think>.
        assertThat(answer).doesNotContain("<think>");
    }
}
