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
package org.apache.camel.component.microprofile.metrics.event.notifier;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.eclipse.microprofile.metrics.MetricRegistry;

public abstract class AbstractMicroProfileMetricsEventNotifier <T extends CamelEvent> extends EventNotifierSupport implements CamelContextAware {

    private final Class<T> eventType;

    private CamelContext camelContext;
    private MetricRegistry metricRegistry;

    public AbstractMicroProfileMetricsEventNotifier(Class<T> eventType) {
        this.eventType = eventType;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    public void setMetricRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @Override
    public boolean isEnabled(CamelEvent eventObject) {
        return eventType.isAssignableFrom(eventObject.getClass());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (metricRegistry == null) {
            metricRegistry = MicroProfileMetricsHelper.getMetricRegistry(camelContext);
        }

        try {
            MicroProfileMetricsEventNotifierService service = camelContext.hasService(MicroProfileMetricsEventNotifierService.class);
            if (service == null) {
                service = new MicroProfileMetricsEventNotifierService();
                service.setMetricRegistry(getMetricRegistry());
                camelContext.addService(service);
                ServiceHelper.startService(service);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
