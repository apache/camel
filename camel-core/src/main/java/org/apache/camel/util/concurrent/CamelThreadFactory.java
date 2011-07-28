package org.apache.camel.util.concurrent;

import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread factory which creates threads supporting a naming pattern.
 */
public final class CamelThreadFactory implements ThreadFactory {
    private static final Logger LOG = LoggerFactory.getLogger(CamelThreadFactory.class);

    private final String pattern;
    private final String name;
    private final boolean daemon;

    public CamelThreadFactory(String pattern, String name, boolean daemon) {
        this.pattern = pattern;
        this.name = name;
        this.daemon = daemon;
    }

    public Thread newThread(Runnable runnable) {
        String threadName = ThreadHelper.resolveThreadName(pattern, name);
        Thread answer = new Thread(runnable, threadName);
        answer.setDaemon(daemon);

        LOG.trace("Created thread[{}]: {}", name, answer);
        return answer;
    }

    public String toString() {
        return "CamelThreadFactory[" + name + "]";
    }
}