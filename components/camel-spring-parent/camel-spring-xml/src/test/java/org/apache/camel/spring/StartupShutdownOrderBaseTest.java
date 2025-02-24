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
package org.apache.camel.spring;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

import static org.hamcrest.MatcherAssert.assertThat;

public abstract class StartupShutdownOrderBaseTest {

    static class AutoCloseableBean implements AutoCloseable, TestState {

        boolean closed;

        private final ApplicationContext context;

        public AutoCloseableBean(final ApplicationContext context) {
            this.context = context;
        }

        @Override
        public void assertValid() {
            assertThat("AutoCloseable bean should be closed", closed);
        }

        @Override
        public void close() {
            assertThat("AutoCloseable bean should be stopped after Camel", camelIsStopped(context));
            closed = true;
        }
    }

    static class Beans {

        @Bean
        AutoCloseableBean autoCloseableBean(final ApplicationContext context) {
            return new AutoCloseableBean(context);
        }

        @Bean
        BeanWithShutdownMethod beanWithCloseMethod(final ApplicationContext context) {
            return new BeanWithShutdownMethod(context);
        }

        @Bean
        DisposeBean disposedBean(final ApplicationContext context) {
            return new DisposeBean(context);
        }

        @Bean
        InitBean initBean(final ApplicationContext context) {
            return new InitBean(context);
        }

        @Bean
        Lifecycle lifecycleBean(final ApplicationContext context) {
            return new LifecycleBean(context);
        }
    }

    static class BeanWithShutdownMethod implements TestState {

        boolean shutdown;

        private final ApplicationContext context;

        public BeanWithShutdownMethod(final ApplicationContext context) {
            this.context = context;
        }

        @Override
        public void assertValid() {
            assertThat("Bean with shutdown method should be shutdown", shutdown);
        }

        public void shutdown() {
            assertThat("@Bean with close() method should be stopped after Camel", camelIsStopped(context));
            shutdown = true;
        }
    }

    static class DisposeBean implements DisposableBean, TestState {

        boolean disposed;

        private final ApplicationContext context;

        public DisposeBean(final ApplicationContext context) {
            this.context = context;
        }

        @Override
        public void assertValid() {
            assertThat("DisposableBean should be disposed", disposed);
        }

        @Override
        public void destroy() throws Exception {
            assertThat("DisposableBean should be stopped after Camel", camelIsStopped(context));
            disposed = true;
        }
    }

    static class InitBean implements InitializingBean, TestState {

        ApplicationContext context;

        boolean initialized;

        @Autowired
        public InitBean(final ApplicationContext context) {
            this.context = context;
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            assertThat("initializing bean should be started before Camel", camelIsStopped(context));
            initialized = true;
        }

        @Override
        public void assertValid() {
            assertThat("InitializingBean should be initialized", initialized);
        }
    }

    static class LifecycleBean implements SmartLifecycle, TestState {

        ApplicationContext context;

        boolean started;

        boolean stopped;

        @Autowired
        public LifecycleBean(final ApplicationContext context) {
            this.context = context;
        }

        @Override
        public void assertValid() {
            assertThat("Lifecycle should have been started", started);
            assertThat("Lifecycle should be stopped", stopped);
        }

        @Override
        public int getPhase() {
            return 0;
        }

        @Override
        public boolean isAutoStartup() {
            return true;
        }

        @Override
        public boolean isRunning() {
            return started;
        }

        @Override
        public void start() {
            assertThat("lifecycle bean should be started before Camel", camelIsStopped(context));
            started = true;
        }

        @Override
        public void stop() {
            assertThat("lifecycle bean should be stopped after Camel", camelIsStopped(context));
            stopped = true;
        }

        @Override
        public void stop(final Runnable callback) {
            stop();
            callback.run();
        }
    }

    interface TestState {
        void assertValid();
    }

    @Test
    public void camelContextShouldBeStartedLastAndStoppedFirst() {
        final ConfigurableApplicationContext context = createContext();

        final CamelContext camelContext = context.getBean(CamelContext.class);
        final Map<String, TestState> testStates = context.getBeansOfType(TestState.class);

        assertThat("Camel context should be started", camelContext.isStarted());

        context.close();

        assertThat("Camel context should be stopped", camelContext.isStopped());
        testStates.values().stream().forEach(TestState::assertValid);
    }

    abstract ConfigurableApplicationContext createContext();

    static CamelContext camel(final ApplicationContext context) {
        return context.getBean(CamelContext.class);
    }

    static boolean camelIsStarted(final ApplicationContext context) {
        return camel(context).isStarted();
    }

    static boolean camelIsStopped(final ApplicationContext context) {
        return !camelIsStarted(context);
    }

}
