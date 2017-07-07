/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.concurrent.SizedScheduledExecutorService;
import org.junit.Ignore;

/**
 * @version 
 */
public class DefaultExecutorServiceManagerTest extends ContextTestSupport {
    
    public void testResolveThreadNameDefaultPattern() throws Exception {
        String foo = context.getExecutorServiceManager().resolveThreadName("foo");
        String bar = context.getExecutorServiceManager().resolveThreadName("bar");

        assertNotSame(foo, bar);
        assertTrue(foo.startsWith("Camel (" + context.getName() + ") thread "));
        assertTrue(foo.endsWith("foo"));
        assertTrue(bar.startsWith("Camel (" + context.getName() + ") thread "));
        assertTrue(bar.endsWith("bar"));
    }

    public void testGetThreadNameCustomPattern() throws Exception {
        context.getExecutorServiceManager().setThreadNamePattern("##counter# - #name#");
        assertEquals("##counter# - #name#", context.getExecutorServiceManager().getThreadNamePattern());
        String foo = context.getExecutorServiceManager().resolveThreadName("foo");
        String bar = context.getExecutorServiceManager().resolveThreadName("bar");

        assertNotSame(foo, bar);
        assertTrue(foo.startsWith("#"));
        assertTrue(foo.endsWith(" - foo"));
        assertTrue(bar.startsWith("#"));
        assertTrue(bar.endsWith(" - bar"));
    }

    public void testGetThreadNameCustomPatternCamelId() throws Exception {
        context.getExecutorServiceManager().setThreadNamePattern("##camelId# - ##counter# - #name#");
        String foo = context.getExecutorServiceManager().resolveThreadName("foo");
        String bar = context.getExecutorServiceManager().resolveThreadName("bar");

        assertNotSame(foo, bar);
        assertTrue(foo.startsWith("#" + context.getName() + " - #"));
        assertTrue(foo.endsWith(" - foo"));
        assertTrue(bar.startsWith("#" + context.getName() + " - #"));
        assertTrue(bar.endsWith(" - bar"));
    }

    public void testGetThreadNameCustomPatternWithDollar() throws Exception {
        context.getExecutorServiceManager().setThreadNamePattern("Hello - #name#");
        String foo = context.getExecutorServiceManager().resolveThreadName("foo$bar");

        assertEquals("Hello - foo$bar", foo);
    }

    public void testGetThreadNameCustomPatternLongName() throws Exception {
        context.getExecutorServiceManager().setThreadNamePattern("##counter# - #longName#");
        String foo = context.getExecutorServiceManager().resolveThreadName("foo?beer=Carlsberg");
        String bar = context.getExecutorServiceManager().resolveThreadName("bar");

        assertNotSame(foo, bar);
        assertTrue(foo.startsWith("#"));
        assertTrue(foo.endsWith(" - foo?beer=Carlsberg"));
        assertTrue(bar.startsWith("#"));
        assertTrue(bar.endsWith(" - bar"));
    }

    public void testGetThreadNameCustomPatternWithParameters() throws Exception {
        context.getExecutorServiceManager().setThreadNamePattern("##counter# - #name#");
        String foo = context.getExecutorServiceManager().resolveThreadName("foo?beer=Carlsberg");
        String bar = context.getExecutorServiceManager().resolveThreadName("bar");

        assertNotSame(foo, bar);
        assertTrue(foo.startsWith("#"));
        assertTrue(foo.endsWith(" - foo"));
        assertTrue(bar.startsWith("#"));
        assertTrue(bar.endsWith(" - bar"));
    }

    public void testGetThreadNameCustomPatternNoCounter() throws Exception {
        context.getExecutorServiceManager().setThreadNamePattern("Cool #name#");
        String foo = context.getExecutorServiceManager().resolveThreadName("foo");
        String bar = context.getExecutorServiceManager().resolveThreadName("bar");

        assertNotSame(foo, bar);
        assertEquals("Cool foo", foo);
        assertEquals("Cool bar", bar);
    }

    public void testGetThreadNameCustomPatternInvalid() throws Exception {
        context.getExecutorServiceManager().setThreadNamePattern("Cool #xxx#");
        try {
            context.getExecutorServiceManager().resolveThreadName("foo");
            fail("Should thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Pattern is invalid: Cool #xxx#", e.getMessage());
        }

        // reset it so we can shutdown properly
        context.getExecutorServiceManager().setThreadNamePattern("Camel Thread #counter# - #name#");
    }

    public void testDefaultThreadPool() throws Exception {
        ExecutorService myPool = context.getExecutorServiceManager().newDefaultThreadPool(this, "myPool");
        assertEquals(false, myPool.isShutdown());

        // should use default settings
        ThreadPoolExecutor executor = (ThreadPoolExecutor) myPool;
        assertEquals(10, executor.getCorePoolSize());
        assertEquals(20, executor.getMaximumPoolSize());
        assertEquals(60, executor.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(1000, executor.getQueue().remainingCapacity());

        context.stop();
        assertEquals(true, myPool.isShutdown());
    }

    public void testDefaultUnboundedQueueThreadPool() throws Exception {
        ThreadPoolProfile custom = new ThreadPoolProfile("custom");
        custom.setPoolSize(10);
        custom.setMaxPoolSize(30);
        custom.setKeepAliveTime(50L);
        custom.setMaxQueueSize(Integer.MAX_VALUE);

        context.getExecutorServiceManager().setDefaultThreadPoolProfile(custom);
        assertEquals(true, custom.isDefaultProfile().booleanValue());

        ExecutorService myPool = context.getExecutorServiceManager().newDefaultThreadPool(this, "myPool");
        assertEquals(false, myPool.isShutdown());

        // should use default settings
        ThreadPoolExecutor executor = (ThreadPoolExecutor) myPool;
        assertEquals(10, executor.getCorePoolSize());
        assertEquals(30, executor.getMaximumPoolSize());
        assertEquals(50, executor.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(Integer.MAX_VALUE, executor.getQueue().remainingCapacity());

        context.stop();
        assertEquals(true, myPool.isShutdown());
    }

    public void testDefaultNoMaxQueueThreadPool() throws Exception {
        ThreadPoolProfile custom = new ThreadPoolProfile("custom");
        custom.setPoolSize(10);
        custom.setMaxPoolSize(30);
        custom.setKeepAliveTime(50L);
        custom.setMaxQueueSize(0);

        context.getExecutorServiceManager().setDefaultThreadPoolProfile(custom);
        assertEquals(true, custom.isDefaultProfile().booleanValue());

        ExecutorService myPool = context.getExecutorServiceManager().newDefaultThreadPool(this, "myPool");
        assertEquals(false, myPool.isShutdown());

        // should use default settings
        ThreadPoolExecutor executor = (ThreadPoolExecutor) myPool;
        assertEquals(10, executor.getCorePoolSize());
        assertEquals(30, executor.getMaximumPoolSize());
        assertEquals(50, executor.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(0, executor.getQueue().remainingCapacity());

        context.stop();
        assertEquals(true, myPool.isShutdown());
    }

    public void testCustomDefaultThreadPool() throws Exception {
        ThreadPoolProfile custom = new ThreadPoolProfile("custom");
        custom.setKeepAliveTime(20L);
        custom.setMaxPoolSize(40);
        custom.setPoolSize(5);
        custom.setMaxQueueSize(2000);

        context.getExecutorServiceManager().setDefaultThreadPoolProfile(custom);
        assertEquals(true, custom.isDefaultProfile().booleanValue());

        ExecutorService myPool = context.getExecutorServiceManager().newDefaultThreadPool(this, "myPool");
        assertEquals(false, myPool.isShutdown());

        // should use default settings
        ThreadPoolExecutor executor = (ThreadPoolExecutor) myPool;
        assertEquals(5, executor.getCorePoolSize());
        assertEquals(40, executor.getMaximumPoolSize());
        assertEquals(20, executor.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(2000, executor.getQueue().remainingCapacity());

        context.stop();
        assertEquals(true, myPool.isShutdown());
    }

    public void testGetThreadPoolProfile() throws Exception {
        assertNull(context.getExecutorServiceManager().getThreadPoolProfile("foo"));

        ThreadPoolProfile foo = new ThreadPoolProfile("foo");
        foo.setKeepAliveTime(20L);
        foo.setMaxPoolSize(40);
        foo.setPoolSize(5);
        foo.setMaxQueueSize(2000);

        context.getExecutorServiceManager().registerThreadPoolProfile(foo);

        assertSame(foo, context.getExecutorServiceManager().getThreadPoolProfile("foo"));
    }

    public void testTwoGetThreadPoolProfile() throws Exception {
        assertNull(context.getExecutorServiceManager().getThreadPoolProfile("foo"));

        ThreadPoolProfile foo = new ThreadPoolProfile("foo");
        foo.setKeepAliveTime(20L);
        foo.setMaxPoolSize(40);
        foo.setPoolSize(5);
        foo.setMaxQueueSize(2000);

        context.getExecutorServiceManager().registerThreadPoolProfile(foo);

        ThreadPoolProfile bar = new ThreadPoolProfile("bar");
        bar.setKeepAliveTime(40L);
        bar.setMaxPoolSize(5);
        bar.setPoolSize(1);
        bar.setMaxQueueSize(100);

        context.getExecutorServiceManager().registerThreadPoolProfile(bar);

        assertSame(foo, context.getExecutorServiceManager().getThreadPoolProfile("foo"));
        assertSame(bar, context.getExecutorServiceManager().getThreadPoolProfile("bar"));
        assertNotSame(foo, bar);

        assertFalse(context.getExecutorServiceManager().getThreadPoolProfile("foo").isDefaultProfile());
        assertFalse(context.getExecutorServiceManager().getThreadPoolProfile("bar").isDefaultProfile());
    }

    public void testGetThreadPoolProfileInheritDefaultValues() throws Exception {
        assertNull(context.getExecutorServiceManager().getThreadPoolProfile("foo"));
        ThreadPoolProfile foo = new ThreadPoolProfile("foo");
        foo.setMaxPoolSize(40);
        context.getExecutorServiceManager().registerThreadPoolProfile(foo);
        assertSame(foo, context.getExecutorServiceManager().getThreadPoolProfile("foo"));

        ExecutorService executor = context.getExecutorServiceManager().newThreadPool(this, "MyPool", "foo");
        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, executor);
        assertEquals(40, tp.getMaximumPoolSize());
        // should inherit the default values
        assertEquals(10, tp.getCorePoolSize());
        assertEquals(60, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals("CallerRuns", tp.getRejectedExecutionHandler().toString());
    }

    public void testGetThreadPoolProfileInheritCustomDefaultValues() throws Exception {
        ThreadPoolProfile newDefault = new ThreadPoolProfile("newDefault");
        newDefault.setKeepAliveTime(30L);
        newDefault.setMaxPoolSize(50);
        newDefault.setPoolSize(5);
        newDefault.setMaxQueueSize(2000);
        newDefault.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);
        context.getExecutorServiceManager().setDefaultThreadPoolProfile(newDefault);

        assertNull(context.getExecutorServiceManager().getThreadPoolProfile("foo"));
        ThreadPoolProfile foo = new ThreadPoolProfile("foo");
        foo.setMaxPoolSize(25);
        foo.setPoolSize(1);
        context.getExecutorServiceManager().registerThreadPoolProfile(foo);
        assertSame(foo, context.getExecutorServiceManager().getThreadPoolProfile("foo"));

        ExecutorService executor = context.getExecutorServiceManager().newThreadPool(this, "MyPool", "foo");

        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, executor);
        assertEquals(25, tp.getMaximumPoolSize());
        // should inherit the default values
        assertEquals(1, tp.getCorePoolSize());
        assertEquals(30, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals("Abort", tp.getRejectedExecutionHandler().toString());
    }

    public void testGetThreadPoolProfileInheritCustomDefaultValues2() throws Exception {
        ThreadPoolProfile newDefault = new ThreadPoolProfile("newDefault");
        // just change the max pool as the default profile should then inherit the old default profile
        newDefault.setMaxPoolSize(50);
        context.getExecutorServiceManager().setDefaultThreadPoolProfile(newDefault);

        assertNull(context.getExecutorServiceManager().getThreadPoolProfile("foo"));
        ThreadPoolProfile foo = new ThreadPoolProfile("foo");
        foo.setPoolSize(1);
        context.getExecutorServiceManager().registerThreadPoolProfile(foo);
        assertSame(foo, context.getExecutorServiceManager().getThreadPoolProfile("foo"));

        ExecutorService executor = context.getExecutorServiceManager().newThreadPool(this, "MyPool", "foo");

        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, executor);
        assertEquals(1, tp.getCorePoolSize());
        // should inherit the default values
        assertEquals(50, tp.getMaximumPoolSize());
        assertEquals(60, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals("CallerRuns", tp.getRejectedExecutionHandler().toString());
    }

    public void testNewThreadPoolProfile() throws Exception {
        assertNull(context.getExecutorServiceManager().getThreadPoolProfile("foo"));

        ThreadPoolProfile foo = new ThreadPoolProfile("foo");
        foo.setKeepAliveTime(20L);
        foo.setMaxPoolSize(40);
        foo.setPoolSize(5);
        foo.setMaxQueueSize(2000);

        ExecutorService pool = context.getExecutorServiceManager().newThreadPool(this, "Cool", foo);
        assertNotNull(pool);

        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, pool);
        assertEquals(20, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(40, tp.getMaximumPoolSize());
        assertEquals(5, tp.getCorePoolSize());
        assertFalse(tp.isShutdown());

        context.stop();

        assertTrue(tp.isShutdown());
    }

    public void testNewThreadPoolProfileById() throws Exception {
        assertNull(context.getExecutorServiceManager().getThreadPoolProfile("foo"));

        ThreadPoolProfile foo = new ThreadPoolProfile("foo");
        foo.setKeepAliveTime(20L);
        foo.setMaxPoolSize(40);
        foo.setPoolSize(5);
        foo.setMaxQueueSize(2000);

        context.getExecutorServiceManager().registerThreadPoolProfile(foo);

        ExecutorService pool = context.getExecutorServiceManager().newThreadPool(this, "Cool", "foo");
        assertNotNull(pool);

        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, pool);
        assertEquals(20, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(40, tp.getMaximumPoolSize());
        assertEquals(5, tp.getCorePoolSize());
        assertFalse(tp.isShutdown());

        context.stop();

        assertTrue(tp.isShutdown());
    }

    public void testNewThreadPoolMinMax() throws Exception {
        ExecutorService pool = context.getExecutorServiceManager().newThreadPool(this, "Cool", 5, 10);
        assertNotNull(pool);

        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, pool);
        assertEquals(60, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(10, tp.getMaximumPoolSize());
        assertEquals(5, tp.getCorePoolSize());
        assertFalse(tp.isShutdown());

        context.stop();

        assertTrue(tp.isShutdown());
    }

    public void testNewFixedThreadPool() throws Exception {
        ExecutorService pool = context.getExecutorServiceManager().newFixedThreadPool(this, "Cool", 5);
        assertNotNull(pool);

        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, pool);
        // a fixed dont use keep alive
        assertEquals("keepAliveTime", 0, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals("maximumPoolSize", 5, tp.getMaximumPoolSize());
        assertEquals(5, tp.getCorePoolSize());
        assertFalse(tp.isShutdown());

        context.stop();

        assertTrue(tp.isShutdown());
    }

    public void testNewSingleThreadExecutor() throws Exception {
        ExecutorService pool = context.getExecutorServiceManager().newSingleThreadExecutor(this, "Cool");
        assertNotNull(pool);

        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, pool);
        // a single dont use keep alive
        assertEquals("keepAliveTime", 0, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals("maximumPoolSize", 1, tp.getMaximumPoolSize());
        assertEquals(1, tp.getCorePoolSize());
        assertFalse(tp.isShutdown());

        context.stop();

        assertTrue(tp.isShutdown());
    }

    public void testNewScheduledThreadPool() throws Exception {
        ExecutorService pool = context.getExecutorServiceManager().newScheduledThreadPool(this, "Cool", 5);
        assertNotNull(pool);

        SizedScheduledExecutorService tp = assertIsInstanceOf(SizedScheduledExecutorService.class, pool);
        // a scheduled dont use keep alive
        assertEquals(0, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(Integer.MAX_VALUE, tp.getMaximumPoolSize());
        assertEquals(5, tp.getCorePoolSize());
        assertFalse(tp.isShutdown());

        context.stop();

        assertTrue(tp.isShutdown());
    }

    public void testNewSingleThreadScheduledExecutor() throws Exception {
        ExecutorService pool = context.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "Cool");
        assertNotNull(pool);

        SizedScheduledExecutorService tp = assertIsInstanceOf(SizedScheduledExecutorService.class, pool);
        // a scheduled dont use keep alive
        assertEquals(0, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(Integer.MAX_VALUE, tp.getMaximumPoolSize());
        assertEquals(1, tp.getCorePoolSize());
        assertFalse(tp.isShutdown());

        context.stop();

        assertTrue(tp.isShutdown());
    }

    public void testNewCachedThreadPool() throws Exception {
        ExecutorService pool = context.getExecutorServiceManager().newCachedThreadPool(this, "Cool");
        assertNotNull(pool);

        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, pool);
        assertEquals(60, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(Integer.MAX_VALUE, tp.getMaximumPoolSize());
        assertEquals(0, tp.getCorePoolSize());
        assertFalse(tp.isShutdown());

        context.stop();

        assertTrue(tp.isShutdown());
    }

    public void testNewScheduledThreadPoolProfileById() throws Exception {
        assertNull(context.getExecutorServiceManager().getThreadPoolProfile("foo"));

        ThreadPoolProfile foo = new ThreadPoolProfile("foo");
        foo.setKeepAliveTime(20L);
        foo.setMaxPoolSize(40);
        foo.setPoolSize(5);
        foo.setMaxQueueSize(2000);

        context.getExecutorServiceManager().registerThreadPoolProfile(foo);

        ExecutorService pool = context.getExecutorServiceManager().newScheduledThreadPool(this, "Cool", "foo");
        assertNotNull(pool);

        SizedScheduledExecutorService tp = assertIsInstanceOf(SizedScheduledExecutorService.class, pool);
        // a scheduled dont use keep alive
        assertEquals(0, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(Integer.MAX_VALUE, tp.getMaximumPoolSize());
        assertEquals(5, tp.getCorePoolSize());
        assertFalse(tp.isShutdown());

        context.stop();

        assertTrue(tp.isShutdown());
    }

    public void testNewThread() throws Exception {
        Thread thread = context.getExecutorServiceManager().newThread("Cool", new Runnable() {
            @Override
            public void run() {
                // noop
            }
        });

        assertNotNull(thread);
        assertTrue(thread.isDaemon());
        assertTrue(thread.getName().contains("Cool"));
    }

    @Ignore("This is a manual test, by looking at the logs")
    public void xxxtestLongShutdownOfThreadPool() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        ExecutorService pool = context.getExecutorServiceManager().newSingleThreadExecutor(this, "Cool");

        pool.execute(new Runnable() {
            @Override
            public void run() {
                log.info("Starting thread");

                // this should take a long time to shutdown
                try {
                    latch.await(42, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    // ignore
                }

                log.info("Existing thread");
            }
        });

        // sleep a bit before shutting down
        Thread.sleep(3000);

        context.getExecutorServiceManager().shutdown(pool);

        assertTrue(pool.isShutdown());
        assertTrue(pool.isTerminated());
    }

}
