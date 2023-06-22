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
package org.apache.camel.component.micrometer.eventnotifier;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.jmx.JmxMeterRegistry;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.component.micrometer.CamelJmxConfig;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.test.junit5.CamelTestSupport;

abstract class AbstractMicrometerEventNotifierTest extends CamelTestSupport {

    @BindToRegistry(MicrometerConstants.METRICS_REGISTRY_NAME)
    protected CompositeMeterRegistry meterRegistry;

    @Override
    protected boolean useJmx() {
        return true;
    }

    public void addRegistry() {
        meterRegistry = new CompositeMeterRegistry();
        meterRegistry.add(new SimpleMeterRegistry());
        meterRegistry.add(new JmxMeterRegistry(CamelJmxConfig.DEFAULT, Clock.SYSTEM, HierarchicalNameMapper.DEFAULT));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        addRegistry();
        AbstractMicrometerEventNotifier<?> eventNotifier = getEventNotifier();
        eventNotifier.setMeterRegistry(meterRegistry);
        eventNotifier.setPrettyPrint(true);
        camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
        return camelContext;
    }

    protected abstract AbstractMicrometerEventNotifier<?> getEventNotifier();
}
