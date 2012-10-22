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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.util.concurrent.ThreadHelper;

/**
 * Unit test to ensure the {@link org.apache.camel.spi.ExecutorServiceStrategy} still
 * works to keep backwards compatibility.
 *
 * @version 
 */
@SuppressWarnings("deprecation")
public class DefaultExecutorServiceStrategyTest extends ContextTestSupport {

    public void testGetThreadNameDefaultPattern() throws Exception {
        String foo = context.getExecutorServiceStrategy().getThreadName("foo");
        String bar = context.getExecutorServiceStrategy().getThreadName("bar");

        assertNotSame(foo, bar);
        assertTrue(foo.startsWith("Camel (" + context.getName() + ") thread "));
        assertTrue(foo.endsWith("foo"));
        assertTrue(bar.startsWith("Camel (" + context.getName() + ") thread "));
        assertTrue(bar.endsWith("bar"));
    }

    public void testGetThreadNameCustomPattern() throws Exception {
        context.getExecutorServiceStrategy().setThreadNamePattern("##counter# - #name#");
        String foo = context.getExecutorServiceStrategy().getThreadName("foo");
        String bar = context.getExecutorServiceStrategy().getThreadName("bar");

        assertNotSame(foo, bar);
        assertTrue(foo.startsWith("#"));
        assertTrue(foo.endsWith(" - foo"));
        assertTrue(bar.startsWith("#"));
        assertTrue(bar.endsWith(" - bar"));
    }

    public void testGetThreadNameCustomPatternCamelId() throws Exception {
        context.getExecutorServiceStrategy().setThreadNamePattern("##camelId# - ##counter# - #name#");
        String foo = context.getExecutorServiceStrategy().getThreadName("foo");
        String bar = context.getExecutorServiceStrategy().getThreadName("bar");

        assertNotSame(foo, bar);
        assertTrue(foo.startsWith("#" + context.getName() + " - #"));
        assertTrue(foo.endsWith(" - foo"));
        assertTrue(bar.startsWith("#" + context.getName() + " - #"));
        assertTrue(bar.endsWith(" - bar"));
    }

    public void testGetThreadNameCustomPatternWithDollar() throws Exception {
        context.getExecutorServiceStrategy().setThreadNamePattern("Hello - #name#");
        String foo = context.getExecutorServiceStrategy().getThreadName("foo$bar");

        assertEquals("Hello - foo$bar", foo);
    }

    public void testGetThreadNameCustomPatternLongName() throws Exception {
        context.getExecutorServiceStrategy().setThreadNamePattern("##counter# - #longName#");
        String foo = context.getExecutorServiceStrategy().getThreadName("foo?beer=Carlsberg");
        String bar = context.getExecutorServiceStrategy().getThreadName("bar");

        assertNotSame(foo, bar);
        assertTrue(foo.startsWith("#"));
        assertTrue(foo.endsWith(" - foo?beer=Carlsberg"));
        assertTrue(bar.startsWith("#"));
        assertTrue(bar.endsWith(" - bar"));
    }

    public void testGetThreadNameCustomPatternWithParameters() throws Exception {
        context.getExecutorServiceStrategy().setThreadNamePattern("##counter# - #name#");
        String foo = context.getExecutorServiceStrategy().getThreadName("foo?beer=Carlsberg");
        String bar = context.getExecutorServiceStrategy().getThreadName("bar");

        assertNotSame(foo, bar);
        assertTrue(foo.startsWith("#"));
        assertTrue(foo.endsWith(" - foo"));
        assertTrue(bar.startsWith("#"));
        assertTrue(bar.endsWith(" - bar"));
    }

    public void testGetThreadNameCustomPatternNoCounter() throws Exception {
        context.getExecutorServiceStrategy().setThreadNamePattern("Cool #name#");
        String foo = context.getExecutorServiceStrategy().getThreadName("foo");
        String bar = context.getExecutorServiceStrategy().getThreadName("bar");

        assertNotSame(foo, bar);
        assertEquals("Cool foo", foo);
        assertEquals("Cool bar", bar);
    }

    public void testGetThreadNameCustomPatternInvalid() throws Exception {
        context.getExecutorServiceStrategy().setThreadNamePattern("Cool #xxx#");
        try {
            context.getExecutorServiceStrategy().getThreadName("foo");
            fail("Should thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Pattern is invalid: Cool #xxx#", e.getMessage());
        }

        // reset it so we can shutdown properly
        context.getExecutorServiceStrategy().setThreadNamePattern(ThreadHelper.DEFAULT_PATTERN);
    }

    public void testDefaultThreadPool() throws Exception {
        ExecutorService myPool = context.getExecutorServiceStrategy().newDefaultThreadPool(this, "myPool");
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
        ThreadPoolProfileSupport custom = new ThreadPoolProfileSupport("custom");
        custom.setPoolSize(10);
        custom.setMaxPoolSize(30);
        custom.setKeepAliveTime(50L);
        custom.setMaxQueueSize(Integer.MAX_VALUE);

        context.getExecutorServiceStrategy().setDefaultThreadPoolProfile(custom);
        assertEquals(true, custom.isDefaultProfile().booleanValue());

        ExecutorService myPool = context.getExecutorServiceStrategy().newDefaultThreadPool(this, "myPool");
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

    public void testCustomDefaultThreadPool() throws Exception {
        ThreadPoolProfileSupport custom = new ThreadPoolProfileSupport("custom");
        custom.setKeepAliveTime(20L);
        custom.setMaxPoolSize(40);
        custom.setPoolSize(5);
        custom.setMaxQueueSize(2000);

        context.getExecutorServiceStrategy().setDefaultThreadPoolProfile(custom);
        assertEquals(true, custom.isDefaultProfile().booleanValue());

        ExecutorService myPool = context.getExecutorServiceStrategy().newDefaultThreadPool(this, "myPool");
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
        assertNull(context.getExecutorServiceStrategy().getThreadPoolProfile("foo"));

        ThreadPoolProfileSupport foo = new ThreadPoolProfileSupport("foo");
        foo.setKeepAliveTime(20L);
        foo.setMaxPoolSize(40);
        foo.setPoolSize(5);
        foo.setMaxQueueSize(2000);

        context.getExecutorServiceStrategy().registerThreadPoolProfile(foo);

        assertSame(foo, context.getExecutorServiceStrategy().getThreadPoolProfile("foo"));
    }

    public void testTwoGetThreadPoolProfile() throws Exception {
        assertNull(context.getExecutorServiceStrategy().getThreadPoolProfile("foo"));

        ThreadPoolProfileSupport foo = new ThreadPoolProfileSupport("foo");
        foo.setKeepAliveTime(20L);
        foo.setMaxPoolSize(40);
        foo.setPoolSize(5);
        foo.setMaxQueueSize(2000);

        context.getExecutorServiceStrategy().registerThreadPoolProfile(foo);

        ThreadPoolProfileSupport bar = new ThreadPoolProfileSupport("bar");
        bar.setKeepAliveTime(40L);
        bar.setMaxPoolSize(5);
        bar.setPoolSize(1);
        bar.setMaxQueueSize(100);

        context.getExecutorServiceStrategy().registerThreadPoolProfile(bar);

        assertSame(foo, context.getExecutorServiceStrategy().getThreadPoolProfile("foo"));
        assertSame(bar, context.getExecutorServiceStrategy().getThreadPoolProfile("bar"));
        assertNotSame(foo, bar);

        assertFalse(context.getExecutorServiceStrategy().getThreadPoolProfile("foo").isDefaultProfile());
        assertFalse(context.getExecutorServiceStrategy().getThreadPoolProfile("bar").isDefaultProfile());
    }

    public void testGetThreadPoolProfileInheritDefaultValues() throws Exception {
        assertNull(context.getExecutorServiceStrategy().getThreadPoolProfile("foo"));
        ThreadPoolProfileSupport foo = new ThreadPoolProfileSupport("foo");
        foo.setMaxPoolSize(40);
        context.getExecutorServiceStrategy().registerThreadPoolProfile(foo);
        assertSame(foo, context.getExecutorServiceStrategy().getThreadPoolProfile("foo"));

        ExecutorService executor = context.getExecutorServiceStrategy().newThreadPool(this, "MyPool", "foo");
        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, executor);
        assertEquals(40, tp.getMaximumPoolSize());
        // should inherit the default values
        assertEquals(10, tp.getCorePoolSize());
        assertEquals(60, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals("CallerRuns", tp.getRejectedExecutionHandler().toString());
    }

    public void testGetThreadPoolProfileInheritCustomDefaultValues() throws Exception {
        ThreadPoolProfileSupport newDefault = new ThreadPoolProfileSupport("newDefault");
        newDefault.setKeepAliveTime(30L);
        newDefault.setMaxPoolSize(50);
        newDefault.setPoolSize(5);
        newDefault.setMaxQueueSize(2000);
        newDefault.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);
        context.getExecutorServiceStrategy().setDefaultThreadPoolProfile(newDefault);

        assertNull(context.getExecutorServiceStrategy().getThreadPoolProfile("foo"));
        ThreadPoolProfileSupport foo = new ThreadPoolProfileSupport("foo");
        foo.setMaxPoolSize(25);
        foo.setPoolSize(1);
        context.getExecutorServiceStrategy().registerThreadPoolProfile(foo);
        assertSame(foo, context.getExecutorServiceStrategy().getThreadPoolProfile("foo"));

        ExecutorService executor = context.getExecutorServiceStrategy().newThreadPool(this, "MyPool", "foo");

        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, executor);
        assertEquals(25, tp.getMaximumPoolSize());
        // should inherit the default values
        assertEquals(1, tp.getCorePoolSize());
        assertEquals(30, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals("Abort", tp.getRejectedExecutionHandler().toString());
    }

    public void testGetThreadPoolProfileInheritCustomDefaultValues2() throws Exception {
        ThreadPoolProfileSupport newDefault = new ThreadPoolProfileSupport("newDefault");
        // just change the max pool as the default profile should then inherit the old default profile
        newDefault.setMaxPoolSize(50);
        context.getExecutorServiceStrategy().setDefaultThreadPoolProfile(newDefault);

        assertNull(context.getExecutorServiceStrategy().getThreadPoolProfile("foo"));
        ThreadPoolProfileSupport foo = new ThreadPoolProfileSupport("foo");
        foo.setPoolSize(1);
        context.getExecutorServiceStrategy().registerThreadPoolProfile(foo);
        assertSame(foo, context.getExecutorServiceStrategy().getThreadPoolProfile("foo"));

        ExecutorService executor = context.getExecutorServiceStrategy().newThreadPool(this, "MyPool", "foo");

        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, executor);
        assertEquals(1, tp.getCorePoolSize());
        // should inherit the default values
        assertEquals(50, tp.getMaximumPoolSize());
        assertEquals(60, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals("CallerRuns", tp.getRejectedExecutionHandler().toString());
    }

    public void testNewThreadPoolProfile() throws Exception {
        assertNull(context.getExecutorServiceStrategy().getThreadPoolProfile("foo"));

        ThreadPoolProfileSupport foo = new ThreadPoolProfileSupport("foo");
        foo.setKeepAliveTime(20L);
        foo.setMaxPoolSize(40);
        foo.setPoolSize(5);
        foo.setMaxQueueSize(2000);

        context.getExecutorServiceStrategy().registerThreadPoolProfile(foo);

        ExecutorService pool = context.getExecutorServiceStrategy().newThreadPool(this, "Cool", "foo");
        assertNotNull(pool);

        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, pool);
        assertEquals(20, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(40, tp.getMaximumPoolSize());
        assertEquals(5, tp.getCorePoolSize());
        assertFalse(tp.isShutdown());

        context.stop();

        assertTrue(tp.isShutdown());
    }

    public void testLookupThreadPoolProfile() throws Exception {
        ExecutorService pool = context.getExecutorServiceStrategy().lookup(this, "Cool", "fooProfile");
        // does not exists yet
        assertNull(pool);

        assertNull(context.getExecutorServiceStrategy().getThreadPoolProfile("fooProfile"));

        ThreadPoolProfileSupport foo = new ThreadPoolProfileSupport("fooProfile");
        foo.setKeepAliveTime(20L);
        foo.setMaxPoolSize(40);
        foo.setPoolSize(5);
        foo.setMaxQueueSize(2000);

        context.getExecutorServiceStrategy().registerThreadPoolProfile(foo);

        pool = context.getExecutorServiceStrategy().lookup(this, "Cool", "fooProfile");
        assertNotNull(pool);

        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, pool);
        assertEquals(20, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals(40, tp.getMaximumPoolSize());
        assertEquals(5, tp.getCorePoolSize());
        assertFalse(tp.isShutdown());

        context.stop();

        assertTrue(tp.isShutdown());
    }

}
