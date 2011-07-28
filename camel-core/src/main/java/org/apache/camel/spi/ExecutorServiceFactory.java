package org.apache.camel.spi;

import java.util.concurrent.ExecutorService;

public interface ExecutorServiceFactory {
    ExecutorService newExecutorService(ThreadPoolProfile profile);
}
