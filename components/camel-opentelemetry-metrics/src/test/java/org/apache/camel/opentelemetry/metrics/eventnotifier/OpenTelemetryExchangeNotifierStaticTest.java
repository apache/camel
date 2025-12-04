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

package org.apache.camel.opentelemetry.metrics.eventnotifier;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_SENT_TIMER;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ENDPOINT_NAME_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class OpenTelemetryExchangeNotifierStaticTest extends OpenTelemetryExchangeEventNotifierDynamicTest {

    @Override
    protected OpenTelemetryExchangeEventNotifier getEventNotifier() {
        OpenTelemetryExchangeEventNotifier eventNotifier = new OpenTelemetryExchangeEventNotifier();
        eventNotifier.setMeter(otelExtension.getOpenTelemetry().getMeter("meterTest"));
        // create single metrics for URI base endpoint, i.e. without query parameters
        eventNotifier.setBaseEndpointURI(true);
        return eventNotifier;
    }

    @Test
    public void testEventNotifier() throws Exception {
        int count = 10;
        MockEndpoint mock = getMockEndpoint("mock://out");
        mock.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody("direct://in", i);
        }

        mock.assertIsSatisfied();

        int nameCount = 0;
        for (PointData pd : getAllPointDataForRouteId(DEFAULT_CAMEL_EXCHANGE_SENT_TIMER, "test")) {
            String name = pd.getAttributes().get(stringKey(ENDPOINT_NAME_ATTRIBUTE));
            if (name != null && name.startsWith("mc://component")) {
                nameCount++;
                assertInstanceOf(HistogramPointData.class, pd);
                HistogramPointData hpd = (HistogramPointData) pd;
                assertEquals(count, hpd.getCount());
            }
        }
        assertEquals(1, nameCount, "Only one measure should be present for 'mc://component' endpoint.");
    }
}
