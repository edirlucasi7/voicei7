package tech.voice.icety.adapter.out.ai;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;

final class ConversationSession {
    final ReentrantLock lock = new ReentrantLock();
    int users;
    final AtomicBoolean playbackInterrupted = new AtomicBoolean();
}
