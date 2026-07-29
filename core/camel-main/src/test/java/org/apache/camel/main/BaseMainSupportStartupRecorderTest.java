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
package org.apache.camel.main;

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.support.startup.DefaultStartupStepRecorder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseMainSupportStartupRecorderTest {

    @Test
    void startupRecorderRuntimeEnabledPropertyReachesDiscoveredRecorder() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext(false)) {
            FactoryFinder factoryFinder = new StartupRecorderFactoryFinder();
            context.getCamelContextExtension().setBootstrapFactoryFinder(factoryFinder);
            TestMain main = new TestMain();
            main.configure().setStartupRecorder("jfr");
            main.addInitialProperty("camel.main.startupRecorderRuntimeEnabled", "true");

            main.configureStartupRecorderForTest(context, factoryFinder);

            StartupStepRecorder recorder = context.getCamelContextExtension().getStartupStepRecorder();
            assertThat(recorder).isInstanceOf(RuntimeEnabledRecorder.class);
            assertThat(recorder.isRuntimeEnabled()).isTrue();
        }
    }

    private static final class TestMain extends Main {

        void configureStartupRecorderForTest(CamelContext context, FactoryFinder factoryFinder) throws Exception {
            configurePropertiesService(context);
            context.getCamelContextExtension().setBootstrapFactoryFinder(factoryFinder);
            configureStartupRecorder(context);
        }
    }

    public static final class RuntimeEnabledRecorder extends DefaultStartupStepRecorder {

        private boolean runtimeEnabled;

        @Override
        public boolean isRuntimeEnabled() {
            return runtimeEnabled;
        }

        @Override
        public void setRuntimeEnabled(boolean runtimeEnabled) {
            this.runtimeEnabled = runtimeEnabled;
        }
    }

    private static final class StartupRecorderFactoryFinder implements FactoryFinder {

        @Override
        public String getResourcePath() {
            return DEFAULT_PATH;
        }

        @Override
        public Optional<Object> newInstance(String key) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> newInstance(String key, Class<T> type) {
            return Optional.empty();
        }

        @Override
        public Optional<Class<?>> findClass(String key) {
            return findOptionalClass(key);
        }

        @Override
        public Optional<Class<?>> findOptionalClass(String key) {
            return StartupStepRecorder.FACTORY.equals(key) ? Optional.of(RuntimeEnabledRecorder.class) : Optional.empty();
        }

        @Override
        public void clear() {
        }
    }
}
