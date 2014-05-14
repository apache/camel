package org.apache.camel.component.rabbitmq.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Basic object pool.
 * It doesn't purge idle objects from pool.
 * Might be replaced by Commons Pool
 */
public abstract class ObjectPool<T> {
	private final Logger logger = LoggerFactory.getLogger(ObjectPool.class);
	/**
	 * Maximum number of objects in pool
	 */
	private final int maxSize;
	/**
	 * Maximum time waiting for object from pool (not including object creation)
	 */
	private final long waitMax;
	/**
	 * Available pooled objects
	 */
	private final BlockingQueue<T> availableQueue;
	/**
	 * Lock used to protect the creation of channels and the openedList
	 */
	private final Lock lock = new ReentrantLock();
	/**
	 * All pooled object (available and used)
	 */
	private final List<T> openedList;
	/**
	 * Pool state.
	 * Closed means channels can not be opened anymore.
	 */
	private boolean closed = false;

	protected ObjectPool(int maxSize, long waitMax) {
		this.maxSize = maxSize;
		this.waitMax = waitMax;
		this.availableQueue = new ArrayBlockingQueue<T>(maxSize);
		this.openedList = new ArrayList<T>(maxSize);
	}

	/**
	 * Create new object to place in pool
	 */
	protected abstract T create() throws Exception;

	/**
	 * Borrow object from pool
	 */
	public T borrow() throws Exception {
		T borrowed = availableQueue.poll();
		if (borrowed == null) {
			T opened = open();
			if (opened == null) { // Max size reached
				try {
					borrowed = availableQueue.poll(waitMax, TimeUnit.MILLISECONDS);
					if (borrowed == null) {
						throw new ObjectPoolException("No object available in pool");
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			} else {
				borrowed = opened;
			}
		} else if (!isValid(borrowed)) {
            closeSilently(borrowed);
            borrowed = borrow();
        }
		return borrowed;
	}

    /**
     * Check if object is still valid
     */
    protected boolean isValid(T object) {
        return true;
    }
	/**
	 * Create and add new object into the pool
	 */
	private T open() throws Exception {
		T opened = null;
		try {
			lock.lock();
			if (!closed && openedList.size() < maxSize) {
				opened = create();
				openedList.add(opened);
			}
		} finally {
			lock.unlock();
		}
		return opened;
	}

	/**
	 * Get available objects count
	 */
	public int getAvailableCount() {
		return availableQueue.size();
	}

	/**
	 * Get overall objects count
	 */
	public int getOpenedCount() {
		try {
			lock.lock();
			return openedList.size();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Return object to pool
	 */
	public void release(T released) {
		if (released != null) {
			availableQueue.offer(released);
		}
	}

	/**
	 * Close object
	 */
	protected abstract void close(T object) throws Exception;

	/**
	 * Stop object pool, all pooled objects will be closed and removed from pool
	 */
	public void close() {
		try {
			lock.lock();
			closed = true;
			for (T opened : openedList) {
                closeSilently(opened);
            }
			openedList.clear();
			availableQueue.clear();
		} finally {
			lock.unlock();
		}
	}

    private void closeSilently(T opened) {
        try {
            close(opened);
        } catch (Exception e) {
            logger.info("Failed to close", e);
        }
    }

    /**
	 * Execute something using an object from pool:
	 * <ol>
	 * <li>Borrow object</li>
	 * <li>Execute callback with object</li>
	 * <li>Return object to pool</li>
	 * </ol>
	 *
	 * @param <R> Return type
	 */
	public <R> R execute(ObjectCallback<T, R> callback) throws Exception {
		T object = null;
		try {
			object = borrow();
			return callback.doWithObject(object);
		} finally {
			release(object);
		}
	}
}

