package org.maslowis.agent.tool.guard;

public class WorkspaceViolationException extends RuntimeException {
    public WorkspaceViolationException(String message) {
        super(message);
    }
}