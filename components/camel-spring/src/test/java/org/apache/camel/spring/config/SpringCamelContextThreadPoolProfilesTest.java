/*
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
package org.apache.camel.spring.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spring.SpringTestSupport;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringCamelContextThreadPoolProfilesTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/config/SpringCamelContextThreadPoolProfilesTest.xml");
    }

    @Test
    public void testLowProfile() throws Exception {
        CamelContext context = getMandatoryBean(CamelContext.class, "camel-C");

        ThreadPoolProfile profile = context.getExecutorServiceManager().getThreadPoolProfile("low");
        assertEquals(1, profile.getPoolSize().intValue());
        assertEquals(5, profile.getMaxPoolSize().intValue());
        assertEquals(null, profile.getKeepAliveTime());
        assertEquals(null, profile.getMaxQueueSize());
        assertEquals(null, profile.getRejectedPolicy());

        // create a thread pool from low
        ExecutorService executor = context.getExecutorServiceManager().newThreadPool(this, "MyLow", "low");
        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, executor);
        assertEquals(1, tp.getCorePoolSize());
        assertEquals(5, tp.getMaximumPoolSize());
        // should inherit default options
        assertEquals(60, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals("CallerRuns", tp.getRejectedExecutionHandler().toString());
    }

    @Test
    public void testBigProfile() throws Exception {
        CamelContext context = getMandatoryBean(CamelContext.class, "camel-C");

        ThreadPoolProfile profile = context.getExecutorServiceManager().getThreadPoolProfile("big");
        assertEquals(50, profile.getPoolSize().intValue());
        assertEquals(100, profile.getMaxPoolSize().intValue());
        assertEquals(ThreadPoolRejectedPolicy.DiscardOldest, profile.getRejectedPolicy());
        assertEquals(null, profile.getKeepAliveTime());
        assertEquals(null, profile.getMaxQueueSize());

        // create a thread pool from big
        ExecutorService executor = context.getExecutorServiceManager().newThreadPool(this, "MyBig", "big");
        ThreadPoolExecutor tp = assertIsInstanceOf(ThreadPoolExecutor.class, executor);
        assertEquals(50, tp.getCorePoolSize());
        assertEquals(100, tp.getMaximumPoolSize());
        // should inherit default options
        assertEquals(60, tp.getKeepAliveTime(TimeUnit.SECONDS));
        assertEquals("DiscardOldest", tp.getRejectedExecutionHandler().toString());
    }

}
