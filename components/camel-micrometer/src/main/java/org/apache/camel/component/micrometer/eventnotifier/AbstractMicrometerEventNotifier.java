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

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.micrometer.MicrometerUtils;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.service.ServiceHelper;

import static org.apache.camel.component.micrometer.MicrometerConstants.METRICS_REGISTRY_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.SERVICE_NAME;

public abstract class AbstractMicrometerEventNotifier<T extends CamelEvent> extends EventNotifierSupport
        implements CamelContextAware {

    private final Class<T> eventType;

    private CamelContext camelContext;
    private MeterRegistry meterRegistry;
    private boolean prettyPrint;
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;

    protected AbstractMicrometerEventNotifier(Class<T> eventType) {
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

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public TimeUnit getDurationUnit() {
        return durationUnit;
    }

    public void setDurationUnit(TimeUnit durationUnit) {
        this.durationUnit = durationUnit;
    }

    @Override
    public boolean isEnabled(CamelEvent eventObject) {
        return eventType.isAssignableFrom(eventObject.getClass());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (meterRegistry == null) {
            meterRegistry = MicrometerUtils.getOrCreateMeterRegistry(camelContext.getRegistry(), METRICS_REGISTRY_NAME);
        }

        try {
            MicrometerEventNotifierService registryService = camelContext.hasService(MicrometerEventNotifierService.class);
            if (registryService == null) {
                registryService = new MicrometerEventNotifierService();
                registryService.setMeterRegistry(getMeterRegistry());
                registryService.setPrettyPrint(isPrettyPrint());
                registryService.setDurationUnit(getDurationUnit());
                registryService.setMatchingTags(Tags.of(SERVICE_NAME, registryService.getClass().getSimpleName()));
                camelContext.addService(registryService);
                // ensure registry service is started
                ServiceHelper.startService(registryService);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

}
