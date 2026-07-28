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

import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import org.apache.camel.CamelContext;
import org.apache.camel.support.LifecycleStrategySupport;

public class CamelJfrRuntimeInstrumentation extends LifecycleStrategySupport {

    static final Class<?>[] RUNTIME_EVENTS = {
            CamelRouteEvent.class, CamelProcessorEvent.class, CamelExchangeEvent.class,
            CamelExchangeSendEvent.class, CamelExchangeFailedEvent.class, CamelRedeliveryEvent.class
    };

    private boolean registered;

    @Override
    public void onContextInitializing(CamelContext context) {
        if (registered) {
            return;
        }
        for (Class<?> c : RUNTIME_EVENTS) {
            FlightRecorder.register(c.asSubclass(Event.class));
        }
        CamelJfrEventNotifier notifier = new CamelJfrEventNotifier();
        notifier.setCamelContext(context);
        context.getManagementStrategy().addEventNotifier(notifier);
        context.addRoutePolicyFactory(new CamelJfrRoutePolicyFactory());
        context.getCamelContextExtension().addInterceptStrategy(new CamelJfrInterceptStrategy());
        registered = true;
    }

    @Override
    public void onContextStopped(CamelContext context) {
        if (!registered) {
            return;
        }
        for (Class<?> c : RUNTIME_EVENTS) {
            FlightRecorder.unregister(c.asSubclass(Event.class));
        }
        registered = false;
    }
}
