package org.maslowis.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class Agent {

    public static final String SYS_PROMPT = """
            You are harness app, a coding agent that works in a local repository. Answer concisely.
            """;

    private final Conversation conversation = new Conversation(SYS_PROMPT);

    private final ChatModel chatModel;

    public String run(String userMessage) {
        conversation.addUserMessage(userMessage);
        Prompt prompt = new Prompt(conversation.toPromptMessages());
        AssistantMessage assistantMessage = Optional.ofNullable(chatModel.call(prompt).getResult())
                .orElseThrow((() -> new RuntimeException("Chat model answer is null")))
                .getOutput();
        conversation.addAssistantMessage(assistantMessage);

        return assistantMessage.getText();
    }

}
