package org.maslowis.agent.console;

import lombok.experimental.UtilityClass;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;

import java.util.stream.Collectors;

@UtilityClass
public class Printer {

    private static final int CHARS_PER_TOKEN = 4;

    public static void printFirstLine(String key, String text, AnsiColor color) {
        IO.println(AnsiOutput.toString(color, key));
        text.lines().forEach(line -> IO.println(AnsiOutput.toString(color, ">\t" + line)));
    }

    public static void printSecondLine(String key, String text, AnsiColor color) {
        IO.println(AnsiOutput.toString(color, "\t" + key));
        text.lines().forEach(line -> IO.println(AnsiOutput.toString(color, "\t>\t" + line)));
    }

    public static String extractText(Message message) {
        var result = "";
        if (message instanceof UserMessage) {
            result = "%s: %s".formatted(message.getMessageType(), message.getText());
        }
        if (message instanceof AssistantMessage) {
            var text = message.getText() == null || message.getText().isBlank()
                       ? ((AssistantMessage) message).getToolCalls().stream()
                                                     .map(t -> t.name() + " " + t.arguments())
                                                     .collect(Collectors.joining("; "))
                       : message.getText();
            result = "%s: %s".formatted(message.getMessageType(), text);
        }
        if (message instanceof ToolResponseMessage) {
            var text = ((ToolResponseMessage) message).getResponses().stream()
                                                      .map(t -> t.name() + " " + t.responseData())
                                                      .collect(Collectors.joining("; "));
            result = "%s: %s".formatted(message.getMessageType(), text);
        }
        return result;
    }

    public static int estimate(String text) {
        return text == null ? 0 : Math.round((float) text.length() / CHARS_PER_TOKEN);
    }
}
