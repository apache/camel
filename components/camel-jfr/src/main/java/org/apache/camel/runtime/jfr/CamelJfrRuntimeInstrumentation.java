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

import java.util.concurrent.atomic.AtomicBoolean;

import jdk.jfr.FlightRecorder;
import org.apache.camel.CamelContext;
import org.apache.camel.support.LifecycleStrategySupport;

/**
 * Installs the camel-jfr runtime instrumentation (event notifier, route policy and processor interceptor) into a
 * {@link CamelContext}.
 * <p>
 * The events are registered with the flight recorder but never unregistered, as {@code FlightRecorder} is JVM global
 * and another {@link CamelContext} in the same JVM may still be using them. Registering by itself emits nothing: the
 * events are only captured while a recording that enables them is running.
 *
 * @since 4.22
 */
public class CamelJfrRuntimeInstrumentation extends LifecycleStrategySupport {

    private final AtomicBoolean registered = new AtomicBoolean();

    public boolean isRegistered() {
        return registered.get();
    }

    @Override
    public void onContextInitializing(CamelContext context) {
        // a stopped context can be initialized again, but the hooks below must only be added once
        if (!registered.compareAndSet(false, true)) {
            return;
        }
        for (CamelJfrEvents event : CamelJfrEvents.values()) {
            FlightRecorder.register(event.getEventClass());
        }
        CamelJfrEventNotifier notifier = new CamelJfrEventNotifier();
        notifier.setCamelContext(context);
        context.getManagementStrategy().addEventNotifier(notifier);
        context.addRoutePolicyFactory(new CamelJfrRoutePolicyFactory());
        context.getCamelContextExtension().addInterceptStrategy(new CamelJfrInterceptStrategy());
    }
}
