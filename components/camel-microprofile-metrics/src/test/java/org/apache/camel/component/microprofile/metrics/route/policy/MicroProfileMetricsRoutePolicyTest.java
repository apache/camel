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
package org.apache.camel.component.microprofile.metrics.route.policy;

import java.util.Arrays;

import io.smallrye.metrics.TagsUtils;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.Test;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME;

public class MicroProfileMetricsRoutePolicyTest extends MicroProfileMetricsTestSupport {

    private static final long DELAY_FOO = 20;
    private static final long DELAY_BAR = 50;

    @Test
    public void testMetricsRoutePolicy() throws Exception {
        int count = 10;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                template.sendBody("direct:foo", "Hello " + i);
            } else {
                template.sendBody("direct:bar", "Hello " + i);
            }
        }

        assertMockEndpointsSatisfied();

        Timer fooTimer = getTimer(DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME);
        assertEquals(count / 2, fooTimer.getCount());

        Snapshot fooSnapshot = fooTimer.getSnapshot();
        assertTrue(fooSnapshot.getMean() > DELAY_FOO);
        assertTrue(fooSnapshot.getMax() > DELAY_FOO);

        String contextTag = "camelContext=" + context.getName();
        String[] tagStrings = new String[] {contextTag, "failed=false", "routeId=foo", "serviceName=MicroProfileMetricsRoutePolicyService"};
        Tag[] tags = TagsUtils.parseTagsAsArray(tagStrings);

        Timer barTimer = MicroProfileMetricsHelper.findMetric(metricRegistry, DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME, Timer.class, Arrays.asList(tags));
        assertEquals(count / 2, barTimer.getCount());

        Snapshot barSnapshot = fooTimer.getSnapshot();
        assertTrue(barSnapshot.getMean() > DELAY_FOO);
        assertTrue(barSnapshot.getMax() > DELAY_FOO);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").delay(DELAY_FOO).to("mock:result");

                from("direct:bar").routeId("bar").delay(DELAY_BAR).to("mock:result");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MicroProfileMetricsRoutePolicyFactory factory = new MicroProfileMetricsRoutePolicyFactory();
        factory.setMetricRegistry(metricRegistry);

        CamelContext camelContext = super.createCamelContext();
        camelContext.addRoutePolicyFactory(factory);
        return camelContext;
    }
}
