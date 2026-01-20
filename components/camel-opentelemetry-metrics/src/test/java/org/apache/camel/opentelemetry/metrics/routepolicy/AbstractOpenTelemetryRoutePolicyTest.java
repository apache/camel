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
package org.apache.camel.opentelemetry.metrics.routepolicy;

import java.util.List;

import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.CamelContext;
import org.apache.camel.opentelemetry.metrics.AbstractOpenTelemetryTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public abstract class AbstractOpenTelemetryRoutePolicyTest extends AbstractOpenTelemetryTestSupport {

    protected OpenTelemetryRoutePolicyFactory createOpenTelemetryRoutePolicyFactory() {
        OpenTelemetryRoutePolicyFactory factory = new OpenTelemetryRoutePolicyFactory();
        factory.getPolicyConfiguration().setContextEnabled(false);
        factory.getPolicyConfiguration().setExcludePattern(null);
        return factory;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        OpenTelemetryRoutePolicyFactory factory = createOpenTelemetryRoutePolicyFactory();
        factory.setCamelContext(context);
        factory.setMeter(otelExtension.getOpenTelemetry().getMeter("meterTest"));
        context.addRoutePolicyFactory(factory);
        context.addService(factory);
        return context;
    }

    // returns the maximum value recorded by the 'long-timer' until it goes back to 0, i.e. messaging is done
    protected long pollLongTimer(String meterName) throws Exception {
        Thread.sleep(250L);
        long max = 0L;
        long curr = 0L;

        for (int i = 0; i < 10; i++) {
            Thread.sleep(250L);

            List<PointData> ls = getAllPointData(meterName);
            assertEquals(1, ls.size(), "Expected one point data");

            for (var pd : ls) {
                assertInstanceOf(LongPointData.class, pd);
                LongPointData lpd = (LongPointData) pd;
                curr = lpd.getValue();
                max = Math.max(max, curr);
            }
            if (curr == 0L) {
                break;
            }
        }
        return max;
    }
}
