package org.apache.camel.spi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Creates ExecutorService and ScheduledExecutorService objects that work with a thread pool for a given ThreadPoolProfile and ThreadFactory.
 * 
 * This interface allows to customize the creation of these objects to adapt camel for application servers and other environments where thread pools
 * should not be created with the jdk methods
 */
public interface ThreadPoolFactory {
    ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory);
    ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory);
}
