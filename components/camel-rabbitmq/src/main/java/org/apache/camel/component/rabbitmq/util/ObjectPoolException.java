package org.apache.camel.component.rabbitmq.util;

/**
 * Exception raised by {@link ObjectPool} when no object is available in pool.
 */
public class ObjectPoolException extends RuntimeException {
	public ObjectPoolException(String message) {
		super(message);
	}

	public ObjectPoolException(String message, Throwable cause) {
		super(message, cause);
	}
}
