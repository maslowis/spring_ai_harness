package org.maslowis.agent.tool;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Getter
@Accessors(fluent = true)
public class LLMToolContext {
    private final FileStateCache fileStateCache;
}
