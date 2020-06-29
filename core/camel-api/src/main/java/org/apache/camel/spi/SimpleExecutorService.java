package org.apache.camel.spi;

import java.util.concurrent.ExecutorService;

/**
 * Marker interface to signal that a {@link ExecutorService} is simple and tasks are either
 * only submitted via {@link ExecutorService#submit(Runnable)} or executed
 * via {@link ExecutorService#execute(Runnable)} methods.
 */
public interface SimpleExecutorService {
}
