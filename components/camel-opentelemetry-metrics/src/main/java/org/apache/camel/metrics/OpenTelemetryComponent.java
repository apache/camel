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
package org.apache.camel.metrics;

import java.util.Map;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.StringHelper;

/**
 * Represents the component that manages Opentelemetry endpoints.
 */
@Component("opentelemetry-metrics")
public class OpenTelemetryComponent extends DefaultComponent {

    public static final InstrumentType DEFAULT_INSTRUMENT_TYPE = InstrumentType.COUNTER;

    @Metadata(label = "advanced")
    private Meter meter;

    public OpenTelemetryComponent() {
        // do nothing
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (meter == null) {
            this.meter = CamelContextHelper.findSingleByType(getCamelContext(), Meter.class);
        }
        if (meter == null) {
            // autoconfigure if possible otherwise falls back to OpenTelemetry.noop()
            this.meter = GlobalOpenTelemetry.get().getMeter("camel");
        }
        if (meter == null) {
            throw new RuntimeCamelException("Could not find any OpenTelemetry meter!");
        }
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String metricsName = getMetricsName(remaining);
        InstrumentType metricsType = getMetricsType(remaining);
        OpenTelemetryEndpoint endpoint = new OpenTelemetryEndpoint(uri, this, meter, metricsType, metricsName);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    String getMetricsName(String remaining) {
        String name = StringHelper.after(remaining, ":");
        return name == null ? remaining : name;
    }

    InstrumentType getMetricsType(String remaining) {
        String type = StringHelper.before(remaining, ":");
        return type == null ? InstrumentType.COUNTER : InstrumentType.getByName(type);
    }

    public Meter getMeter() {
        return meter;
    }

    /**
     * To use a custom configured Meter.
     */
    public void setMeter(Meter meter) {
        this.meter = meter;
    }
}
