package io.agentkit.core.memory;

import java.util.List;

public interface WorkingMemoryPort {

    List<ChatMessage> getMessages(String sessionId);

    void appendMessage(String sessionId, ChatMessage message);

    void clear(String sessionId);

    boolean isExpired(String sessionId);
}
