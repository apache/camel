package org.apache.camel.component.rabbitmq.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Unit test for {@link ObjectPool}
 */
public class ObjectPoolTest {
	/**
	 * Pooled object
	 */
	public static class Pooled {
		private String ownerName;

		private synchronized void setThreadName() {
			String currentName = Thread.currentThread().getName();
			if (ownerName != null && !ownerName.equals(currentName)) {
				fail("Pooled object aleady owned by different thread");
			} else {
				ownerName = currentName;
			}
		}

		public synchronized void close() {
			ownerName = "closed";
		}

		public synchronized void unsetThreadName() {
			ownerName = null;
		}
	}

	/**
	 * Pooled object pool
	 */
	public static class PooledPool extends ObjectPool<Pooled> {
		public PooledPool(int maxSize) {
			super(maxSize, 1000L);
		}

		@Override
		protected Pooled create() {
			return new Pooled();
		}

		@Override
		protected void close(Pooled object) {
			object.close();
		}
	}

	private final PooledPool pooledPool = new PooledPool(10);

	/**
	 * Basic test
	 * @throws Exception
	 */
	@Test
	public void testBorrowAndRelease() throws Exception {
		assertEquals(0, pooledPool.getAvailableCount());
		assertEquals(0, pooledPool.getOpenedCount());

		Pooled pooled = pooledPool.borrow();
		assertEquals(0, pooledPool.getAvailableCount());
		assertEquals(1, pooledPool.getOpenedCount());

		pooledPool.release(pooled);
		assertEquals(1, pooledPool.getAvailableCount());
		assertEquals(1, pooledPool.getOpenedCount());

		pooled = pooledPool.borrow();
		assertEquals(0, pooledPool.getAvailableCount());
		assertEquals(1, pooledPool.getOpenedCount());

		pooledPool.release(pooled);
		assertEquals(1, pooledPool.getAvailableCount());
		assertEquals(1, pooledPool.getOpenedCount());

		pooledPool.close();
		assertEquals(0, pooledPool.getAvailableCount());
		assertEquals(0, pooledPool.getOpenedCount());
	}

	/**
	 * Test when max size is reached, borrow returns null
	 */
	@Test(expected = ObjectPoolException.class)
	public void testMaxSize_ObjectPoolException() throws Exception {
		assertEquals(0, pooledPool.getAvailableCount());
		assertEquals(0, pooledPool.getOpenedCount());

		Pooled[] pooleds = new Pooled[10];
		for (int i = 0; i < 10; i++) {
			pooleds[i] = pooledPool.borrow();
		}
		assertEquals(0, pooledPool.getAvailableCount());
		assertEquals(10, pooledPool.getOpenedCount());

		assertNull(pooledPool.borrow());
	}

	/**
	 * Thread using pooled object
	 */
	public class PooledUserRunnable implements Runnable {
		private final int iterations;

		public PooledUserRunnable(CountDownLatch countDownLatch, int iterations) {
			this.iterations = iterations;
		}

		@Override
		public void run() {
			try {
				for (int i = 0; i < iterations; i++) {
					Pooled pooled = pooledPool.borrow();
					if (pooled != null) {
						pooled.setThreadName();
						Thread.sleep(50L);
						pooled.unsetThreadName();
						pooledPool.release(pooled);
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static class CountingUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
		private List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<Throwable>());

		@Override
		public void uncaughtException(Thread t, Throwable e) {
			exceptions.add(e);
		}

		public int getExceptionCount() {
			return exceptions.size();
		}
	}

	/**
	 * Test using multiple threads to check that locking is properly working
	 */
	@Test
	public void testMultiThread() throws InterruptedException {
		assertEquals(0, pooledPool.getAvailableCount());
		assertEquals(0, pooledPool.getOpenedCount());

		int threadCount = 15;
		Thread[] threads = new Thread[threadCount];
		PooledUserRunnable[] runnables = new PooledUserRunnable[threadCount];
		CountDownLatch countDownLatch = new CountDownLatch(threadCount);
		final CountingUncaughtExceptionHandler uncaughtExceptionHandler = new CountingUncaughtExceptionHandler();
		for (int i = 0; i < threadCount; i++) {
			runnables[i] = new PooledUserRunnable(countDownLatch, 100);
			threads[i] = new Thread(runnables[i], "PooledUser#" + i);
			threads[i].setUncaughtExceptionHandler(uncaughtExceptionHandler);
		}

		for (Thread thread : threads) {
			thread.start();
		}

		for (Thread thread : threads) {
			thread.join();
		}

		assertEquals(0, uncaughtExceptionHandler.getExceptionCount());
	}

	/**
	 * Test using execute(callback).
	 */
	@Test
	public void testExecute() throws Exception {
		assertEquals(0, pooledPool.getAvailableCount());
		assertEquals(0, pooledPool.getOpenedCount());

		assertTrue(pooledPool.execute(new ObjectCallback<Pooled, Boolean>() {
			@Override
			public Boolean doWithObject(Pooled object) throws Exception {
				return object != null;
			}
		}));

		assertEquals(1, pooledPool.getAvailableCount());
		assertEquals(1, pooledPool.getOpenedCount());

		assertTrue(pooledPool.execute(new ObjectCallback<Pooled, Boolean>() {
			@Override
			public Boolean doWithObject(Pooled object) throws Exception {
				return object != null;
			}
		}));

		assertEquals(1, pooledPool.getAvailableCount());
		assertEquals(1, pooledPool.getOpenedCount());
	}

	/**
	 * Test using execute(callback) and callback raises un exception.
	 */
	@Test
	public void testExecute_Exception() throws Exception {
		assertEquals(0, pooledPool.getAvailableCount());
		assertEquals(0, pooledPool.getOpenedCount());

		try {
			pooledPool.execute(new ObjectCallback<Pooled, Boolean>() {
				@Override
				public Boolean doWithObject(Pooled object) throws Exception {
					throw new IllegalStateException();
				}
			});
			fail("IllegalStateException expected");
		} catch (IllegalStateException e) {
			// Expected
		}

		assertEquals(1, pooledPool.getAvailableCount());
		assertEquals(1, pooledPool.getOpenedCount());
	}
}
