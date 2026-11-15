package org.maslowis.agent;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfSystemProperty(named = "spring.profiles.active", matches = "IT")
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentOllamaIT {

    @Autowired
    Agent agent;

    @Test
    @Order(1)
    void testOllamaAnswer() {
        String answer = agent.run("Reply with exactly one short sentence about yourself.");
        assertThat(answer).isNotBlank();
        // Если think-option: false не подхватился, Ollama вернёт рассуждения модели
        // в отдельном поле, а некоторые сборки — прямо в тексте тегами <think>.
        assertThat(answer).doesNotContain("<think>");
    }

    @Test
    @Order(2)
    void testOllamaReadFile() {
        String answer = agent.run("What Java version is used in the project?");
        assertThat(answer).isNotBlank();
        // Если think-option: false не подхватился, Ollama вернёт рассуждения модели
        // в отдельном поле, а некоторые сборки — прямо в тексте тегами <think>.
        assertThat(answer).doesNotContain("<think>");
    }

    @Test
    @Order(3)
    void testOllamaAnswerFindClass() {
        String answer = agent.run("In which file is the class LLMToolExecutor declared? Give the path and explain what this class does.");
        assertThat(answer).isNotBlank();
        // Если think-option: false не подхватился, Ollama вернёт рассуждения модели
        // в отдельном поле, а некоторые сборки — прямо в тексте тегами <think>.
        assertThat(answer).doesNotContain("<think>");
    }

    @Test
    @Order(4)
    void testOllamaAnswerFindCode() {
        String answer = agent.run("And who listens to ToolCallEnd? Find the file.");
        assertThat(answer).isNotBlank();
        // Если think-option: false не подхватился, Ollama вернёт рассуждения модели
        // в отдельном поле, а некоторые сборки — прямо в тексте тегами <think>.
        assertThat(answer).doesNotContain("<think>");
    }

    @Test
    @Order(5)
    void testOllamaAnswerEditCode() {
        String answer = agent.run("The `TargetPractice` java class does not have the `packageOf` method implemented. Locate the file, read it, and implement this method. " +
                                  "The method takes a `Path` as an argument and returns a string representing the Java package name (with components separated by dots) from a `Path` " +
                                  "Return string like org.maslowis.agent.event.");
        assertThat(answer).isNotBlank();
        // Если think-option: false не подхватился, Ollama вернёт рассуждения модели
        // в отдельном поле, а некоторые сборки — прямо в тексте тегами <think>.
        assertThat(answer).doesNotContain("<think>");
    }
}
