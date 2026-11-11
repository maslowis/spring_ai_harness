package org.maslowis.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("harness.agent")
public record AgentProperties(
        @DefaultValue("20") int maxTurns,
        @DefaultValue(".") String workspaceRoot
) {

    public AgentProperties {
        if (maxTurns < 1) {
            throw new IllegalArgumentException("harness.agent.max-turns should be >= 1, but put: " + maxTurns);
        }
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            throw new IllegalArgumentException("harness.agent.workspace-root can't be empty");
        }
    }
}
