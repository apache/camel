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
package org.apache.camel.spring;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ServiceSupport;
import org.junit.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class StartupShutdownOrderBaseTest {

    static class AutoCloseableBean implements AutoCloseable, TestState {

        boolean closed;

        private final ApplicationContext context;

        public AutoCloseableBean(final ApplicationContext context) {
            this.context = context;
        }

        @Override
        public void assertValid() {
            assertThat(closed).as("AutoCloseable bean should be closed").isTrue();
        }

        @Override
        public void close() {
            assertThat(camelIsStopped(context)).as("AutoCloseable bean should be stopped after Camel").isTrue();
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
            assertThat(shutdown).as("Bean with shutdown method should be shutdown").isTrue();
        }

        public void shutdown() {
            assertThat(camelIsStopped(context)).as("@Bean with close() method should be stopped after Camel").isTrue();
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
            assertThat(disposed).as("DisposableBean should be disposed").isTrue();
        }

        @Override
        public void destroy() throws Exception {
            assertThat(camelIsStopped(context)).as("DisposableBean should be stopped after Camel").isTrue();
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
            assertThat(camelIsStopped(context)).as("initializing bean should be started before Camel").isTrue();
            initialized = true;
        }

        @Override
        public void assertValid() {
            assertThat(initialized).as("InitializingBean should be initialized").isTrue();
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
            assertThat(started).as("Lifecycle should have been started").isTrue();
            assertThat(stopped).as("Lifecycle should be stopped").isTrue();
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
            assertThat(camelIsStopped(context)).as("lifecycle bean should be started before Camel").isTrue();
            started = true;
        }

        @Override
        public void stop() {
            assertThat(camelIsStopped(context)).as("lifecycle bean should be stopped after Camel").isTrue();
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

        final ServiceSupport camelContext = (ServiceSupport) context.getBean(CamelContext.class);
        final Map<String, TestState> testStates = context.getBeansOfType(TestState.class);

        assertThat(camelContext.isStarted()).as("Camel context should be started").isTrue();

        context.close();

        assertThat(camelContext.isStopped()).as("Camel context should be stopped").isTrue();
        testStates.values().stream().forEach(TestState::assertValid);
    }

    abstract ConfigurableApplicationContext createContext();

    static ServiceSupport camel(final ApplicationContext context) {
        return (ServiceSupport) context.getBean(CamelContext.class);
    }

    static boolean camelIsStarted(final ApplicationContext context) {
        return camel(context).isStarted();
    }

    static boolean camelIsStopped(final ApplicationContext context) {
        return !camelIsStarted(context);
    }

}
