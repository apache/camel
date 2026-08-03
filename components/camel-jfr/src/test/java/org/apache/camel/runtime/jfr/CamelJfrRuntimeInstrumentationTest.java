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
package org.apache.camel.runtime.jfr;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelJfrRuntimeInstrumentationTest {

    private CamelContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void hooksAreInstalledOnContextInitializing() {
        CamelJfrRuntimeInstrumentation instrumentation = new CamelJfrRuntimeInstrumentation();
        assertThat(instrumentation.isRegistered()).isFalse();

        context = new DefaultCamelContext();
        context.addLifecycleStrategy(instrumentation);
        context.start();

        assertThat(instrumentation.isRegistered()).isTrue();
        assertThat(context.getManagementStrategy().getEventNotifiers())
                .hasAtLeastOneElementOfType(CamelJfrEventNotifier.class);
        assertThat(context.getRoutePolicyFactories()).hasAtLeastOneElementOfType(CamelJfrRoutePolicyFactory.class);
        assertThat(context.getCamelContextExtension().getInterceptStrategies())
                .hasAtLeastOneElementOfType(CamelJfrInterceptStrategy.class);
    }

    @Test
    void registrationSurvivesAStopBecauseFlightRecorderIsJvmGlobal() {
        // FlightRecorder.register/unregister is JVM wide, so unregistering on stop would blind every other
        // CamelContext in the same JVM. Stopping one context must therefore leave the event types registered.
        CamelJfrRuntimeInstrumentation instrumentation = new CamelJfrRuntimeInstrumentation();
        context = new DefaultCamelContext();
        context.addLifecycleStrategy(instrumentation);
        context.start();
        assertThat(instrumentation.isRegistered()).isTrue();

        context.stop();

        assertThat(instrumentation.isRegistered()).isTrue();
    }

    @Test
    void restartDoesNotInstallTheHooksTwice() {
        // a stopped context can be initialized again, and every duplicate notifier/policy/interceptor would emit
        // a duplicate JFR event for the same message
        CamelJfrRuntimeInstrumentation instrumentation = new CamelJfrRuntimeInstrumentation();
        context = new DefaultCamelContext();
        context.addLifecycleStrategy(instrumentation);
        context.start();

        long notifiers = countJfrNotifiers();
        long policyFactories = countJfrRoutePolicyFactories();
        long interceptors = countJfrInterceptStrategies();
        assertThat(notifiers).isOne();
        assertThat(policyFactories).isOne();
        assertThat(interceptors).isOne();

        context.stop();
        context.start();

        assertThat(countJfrNotifiers()).isEqualTo(notifiers);
        assertThat(countJfrRoutePolicyFactories()).isEqualTo(policyFactories);
        assertThat(countJfrInterceptStrategies()).isEqualTo(interceptors);
    }

    private long countJfrNotifiers() {
        return context.getManagementStrategy().getEventNotifiers().stream()
                .filter(CamelJfrEventNotifier.class::isInstance).count();
    }

    private long countJfrRoutePolicyFactories() {
        return context.getRoutePolicyFactories().stream()
                .filter(CamelJfrRoutePolicyFactory.class::isInstance).count();
    }

    private long countJfrInterceptStrategies() {
        return context.getCamelContextExtension().getInterceptStrategies().stream()
                .filter(CamelJfrInterceptStrategy.class::isInstance).count();
    }
}
