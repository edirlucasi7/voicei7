package tech.voice.icety.adapter.out.ai;

import java.util.concurrent.locks.ReentrantLock;

final class ConversationSession {
    final ReentrantLock lock = new ReentrantLock();
    int users;
}
