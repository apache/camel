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
package org.apache.camel.component.microprofile.metrics.event.notifier.context;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper;
import org.apache.camel.component.microprofile.metrics.event.notifier.AbstractMicroProfileMetricsEventNotifier;
import org.apache.camel.component.microprofile.metrics.gauge.LambdaGauge;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.CamelContextStartingEvent;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_STATUS_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_STATUS_DISPLAY_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_UPTIME_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_UPTIME_DISPLAY_NAME;

public class MicroProfileMetricsCamelContextEventNotifier extends AbstractMicroProfileMetricsEventNotifier<CamelContextStartingEvent> {

    private MicroProfileMetricsCamelContextEventNotifierNamingStrategy namingStrategy = MicroProfileMetricsCamelContextEventNotifierNamingStrategy.DEFAULT;

    public MicroProfileMetricsCamelContextEventNotifier() {
        super(CamelContextStartingEvent.class);
    }

    public MicroProfileMetricsCamelContextEventNotifierNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(MicroProfileMetricsCamelContextEventNotifierNamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        if (event instanceof CamelContextStartingEvent) {
            registerCamelContextMetrics(((CamelContextStartingEvent) event).getContext());
        }
    }

    public void registerCamelContextMetrics(CamelContext camelContext) {
        MetricRegistry metricRegistry = getMetricRegistry();

        Tag[] tags = namingStrategy.getTags(camelContext);

        Metadata uptimeMetadata = new MetadataBuilder()
            .withName(namingStrategy.getCamelContextUptimeName())
            .withDisplayName(CAMEL_CONTEXT_UPTIME_DISPLAY_NAME)
            .withDescription(CAMEL_CONTEXT_UPTIME_DESCRIPTION)
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.MILLISECONDS)
            .build();
        metricRegistry.register(uptimeMetadata, new LambdaGauge(() -> camelContext.getUptimeMillis()), tags);

        Metadata statusMetadata = new MetadataBuilder()
            .withName(namingStrategy.getCamelContextStatusName())
            .withDisplayName(CAMEL_CONTEXT_STATUS_DISPLAY_NAME)
            .withDescription(CAMEL_CONTEXT_STATUS_DESCRIPTION)
            .withType(MetricType.GAUGE)
            .build();
        metricRegistry.register(statusMetadata, new LambdaGauge(() -> camelContext.getStatus().ordinal()), tags);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        MicroProfileMetricsHelper.removeMetricsFromRegistry(getMetricRegistry(), (metricID, metric) -> {
            String name = metricID.getName();
            Map<String, String> tags = metricID.getTags();
            if (name.equals(namingStrategy.getCamelContextStatusName()) || name.equals(namingStrategy.getCamelContextUptimeName())) {
                if (tags.containsKey(CAMEL_CONTEXT_TAG)) {
                    return tags.get(CAMEL_CONTEXT_TAG).equals(getCamelContext().getName());
                }
            }
            return false;
        });
    }
}
