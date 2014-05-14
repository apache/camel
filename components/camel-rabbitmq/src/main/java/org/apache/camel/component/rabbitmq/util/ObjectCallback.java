package org.apache.camel.component.rabbitmq.util;

/**
 * Callback used by {@link ObjectPool}
 * @param <T> Pooled object type
 * @param <R> Result type
 */
public interface ObjectCallback<T, R> {
	/**
	 * @param object Pooled object
	 */
	R doWithObject(T object) throws Exception;
}
