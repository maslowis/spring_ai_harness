package org.maslowis;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.maslowis.agent.Agent;
import org.maslowis.agent.ContentUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
@Profile("!test")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ChatCommandLineRunner implements CommandLineRunner {

    private final Agent agent;

    @Override
    public void run(String @NonNull ... args) {
        var input = getInput(args);
        if (ContentUtils.isBlank(input)) {
            return;
        }
        agent.run(input);
    }

    private String getInput(String[] args) {
        return Arrays.stream(args)
                     .filter(ContentUtils::isNotBlank)
                     .collect(Collectors.joining("\n"));
    }
}
