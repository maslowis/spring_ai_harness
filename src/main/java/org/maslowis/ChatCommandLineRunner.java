package org.maslowis;

import lombok.RequiredArgsConstructor;
import org.maslowis.agent.Agent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Profile("!test")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ChatCommandLineRunner implements CommandLineRunner {

    private final Agent agent;

    @Override
    public void run(String... args) {
        String input = getInput(args);
        if (!input.isBlank()) {
            agent.run(input);
        }
    }

    private String getInput(String[] args) {
        return Stream.ofNullable(args)
                .flatMap(Arrays::stream)
                .filter(a -> a != null && !a.isBlank())
                .collect(Collectors.joining("; "));
    }
}
