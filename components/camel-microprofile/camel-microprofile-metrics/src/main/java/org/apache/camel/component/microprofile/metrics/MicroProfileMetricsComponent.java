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
package org.apache.camel.component.microprofile.metrics;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_TAG;

@Component("microprofile-metrics")
public class MicroProfileMetricsComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private MetricRegistry metricRegistry;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (metricRegistry == null) {
            metricRegistry = MicroProfileMetricsHelper.getMetricRegistry(getCamelContext());
        }

        String metricsName = MicroProfileMetricsHelper.getMetricsName(remaining);
        MetricType metricsType = MicroProfileMetricsHelper.getMetricsType(remaining);

        return new MicroProfileMetricsEndpoint(uri, this, metricRegistry, metricsType, metricsName);
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    /**
     * Use a custom MetricRegistry.
     */
    public void setMetricRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @Override
    protected void doStop() throws Exception {
        MicroProfileMetricsHelper.removeMetricsFromRegistry(metricRegistry, (metricID, metric) -> {
            Map<String, String> tags = metricID.getTags();
            if (tags.containsKey(CAMEL_CONTEXT_TAG)) {
                return tags.get(CAMEL_CONTEXT_TAG).equals(getCamelContext().getName());
            }
            return false;
        });
    }
}
