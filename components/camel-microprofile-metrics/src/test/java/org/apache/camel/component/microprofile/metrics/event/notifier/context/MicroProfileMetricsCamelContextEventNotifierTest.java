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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsTestSupport;
import org.apache.camel.component.microprofile.metrics.gauge.LambdaGauge;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_STATUS_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_UPTIME_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper.findMetric;

public class MicroProfileMetricsCamelContextEventNotifierTest extends MicroProfileMetricsTestSupport {

    private MicroProfileMetricsCamelContextEventNotifier eventNotifier;

    @Test
    public void testMicroProfileMetricsCamelContextEventNotifier() throws Exception {
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag(CAMEL_CONTEXT_TAG, context.getName()));

        LambdaGauge uptime = findMetric(metricRegistry, CAMEL_CONTEXT_UPTIME_METRIC_NAME, LambdaGauge.class, tags);
        assertNotNull(uptime);
        assertTrue(uptime.getValue().intValue() > 0);

        LambdaGauge status = findMetric(metricRegistry, CAMEL_CONTEXT_STATUS_METRIC_NAME, LambdaGauge.class, tags);
        assertNotNull(status);
        assertEquals(ServiceStatus.Started.ordinal(), status.getValue().intValue());

        context.stop();
        assertEquals(ServiceStatus.Stopped.ordinal(), status.getValue().intValue());
    }

    @Test
    public void testMicroProfileMetricsCamelContextEventNotifierStop() throws Exception {
        assertEquals(2, metricRegistry.getMetrics().size());
        eventNotifier.stop();
        assertEquals(0, metricRegistry.getMetrics().size());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        eventNotifier = new MicroProfileMetricsCamelContextEventNotifier();
        camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
        return camelContext;
    }
}
