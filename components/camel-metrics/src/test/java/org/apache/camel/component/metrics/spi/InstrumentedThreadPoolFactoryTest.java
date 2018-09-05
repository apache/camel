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
package org.apache.camel.component.metrics.spi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.InstrumentedScheduledExecutorService;
import com.codahale.metrics.MetricRegistry;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;

@RunWith(MockitoJUnitRunner.class)
public class InstrumentedThreadPoolFactoryTest {

    private static final String METRICS_NAME = "metrics.name";

    @Mock
    private MetricRegistry registry;

    @Mock
    private ThreadPoolFactory threadPoolFactory;

    @Mock
    private ThreadFactory threadFactory;

    private ThreadPoolProfile profile;

    private InstrumentedThreadPoolFactory instrumentedThreadPoolFactory;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        profile = new ThreadPoolProfile(METRICS_NAME);
        profile.setDefaultProfile(false);
        profile.setMaxPoolSize(10);
        profile.setMaxQueueSize(1000);
        profile.setPoolSize(5);
        profile.setKeepAliveTime(5L);
        profile.setTimeUnit(TimeUnit.SECONDS);
        profile.setRejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns);

        instrumentedThreadPoolFactory = new InstrumentedThreadPoolFactory(registry, threadPoolFactory);

        inOrder = Mockito.inOrder(registry);
    }

    @Test
    public void testNewCacheThreadPool() throws Exception {
        final ExecutorService executorService = instrumentedThreadPoolFactory.newCachedThreadPool(threadFactory);
        assertThat(executorService, is(notNullValue()));
        assertThat(executorService, is(instanceOf(InstrumentedExecutorService.class)));

        inOrder.verify(registry, times(1)).meter(anyString());
        inOrder.verify(registry, times(1)).counter(anyString());
        inOrder.verify(registry, times(1)).meter(anyString());
        inOrder.verify(registry, times(1)).timer(anyString());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testNewThreadPool() throws Exception {
        final ExecutorService executorService = instrumentedThreadPoolFactory.newThreadPool(profile, threadFactory);
        assertThat(executorService, is(notNullValue()));
        assertThat(executorService, is(instanceOf(InstrumentedExecutorService.class)));

        inOrder.verify(registry, times(1)).meter(MetricRegistry.name(METRICS_NAME, new String[]{"submitted"}));
        inOrder.verify(registry, times(1)).counter(MetricRegistry.name(METRICS_NAME, new String[]{"running"}));
        inOrder.verify(registry, times(1)).meter(MetricRegistry.name(METRICS_NAME, new String[]{"completed"}));
        inOrder.verify(registry, times(1)).timer(MetricRegistry.name(METRICS_NAME, new String[]{"duration"}));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testNewScheduledThreadPool() throws Exception {
        final ScheduledExecutorService scheduledExecutorService = instrumentedThreadPoolFactory.newScheduledThreadPool(profile, threadFactory);

        assertThat(scheduledExecutorService, is(notNullValue()));
        assertThat(scheduledExecutorService, is(instanceOf(InstrumentedScheduledExecutorService.class)));

        inOrder.verify(registry, times(1)).meter(MetricRegistry.name(METRICS_NAME, new String[]{"submitted"}));
        inOrder.verify(registry, times(1)).counter(MetricRegistry.name(METRICS_NAME, new String[]{"running"}));
        inOrder.verify(registry, times(1)).meter(MetricRegistry.name(METRICS_NAME, new String[]{"completed"}));
        inOrder.verify(registry, times(1)).timer(MetricRegistry.name(METRICS_NAME, new String[]{"duration"}));
        inOrder.verify(registry, times(1)).meter(MetricRegistry.name(METRICS_NAME, new String[]{"scheduled.once"}));
        inOrder.verify(registry, times(1)).meter(MetricRegistry.name(METRICS_NAME, new String[]{"scheduled.repetitively"}));
        inOrder.verify(registry, times(1)).counter(MetricRegistry.name(METRICS_NAME, new String[]{"scheduled.overrun"}));
        inOrder.verify(registry, times(1)).histogram(MetricRegistry.name(METRICS_NAME, new String[]{"scheduled.percent-of-period"}));
        inOrder.verifyNoMoreInteractions();
    }

}
