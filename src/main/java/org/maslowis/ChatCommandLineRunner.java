package org.maslowis;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.maslowis.agent.Agent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@Profile("!test")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ChatCommandLineRunner implements CommandLineRunner {

    private final Agent agent;

    @Override
    public void run(String @NonNull ... args) {
        var messages = getInput(args);
        if (!messages.isEmpty()) {
            messages.forEach(agent::run);
        }
    }

    private List<String> getInput(String[] args) {
        return Arrays.stream(Optional.ofNullable(args).orElse(new String[0]))
                     .filter(message -> message != null && !message.isBlank())
                     .toList();
    }
}
