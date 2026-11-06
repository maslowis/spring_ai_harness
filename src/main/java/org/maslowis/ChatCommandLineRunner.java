package org.maslowis;

import lombok.RequiredArgsConstructor;
import org.maslowis.agent.Agent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ChatCommandLineRunner implements CommandLineRunner {

    private final Agent agent;

    @Override
    public void run(String... args) {
        IO.println(agent.run("Кто ты? Ответь одним предложением."));
    }

}
