package org.maslowis.agent;

import org.springframework.ai.chat.messages.*;

import java.util.ArrayList;
import java.util.List;

public class Conversation {

    private final SystemMessage systemMessage;
    private final List<Message> messages = new ArrayList<>();

    public Conversation(String systemPrompt) {
        this.systemMessage = new SystemMessage(systemPrompt);
    }

    public void addUserMessage(String userMessage) {
        addUserMessage(new UserMessage(userMessage));
    }

    public void addUserMessage(UserMessage userMessage) {
        messages.add(userMessage);
    }

    public void addAssistantMessage(AssistantMessage assistantMessage) {
        messages.add(assistantMessage);
    }

    public void addToolResponseMessage(ToolResponseMessage toolResponseMessage) {
        messages.add(toolResponseMessage);
    }

    public List<Message> toPromptMessages() {
        List<Message> promptMessages = new ArrayList<>(messages.size() + 1);
        promptMessages.add(systemMessage);
        promptMessages.addAll(messages);
        return promptMessages;
    }
}
