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
package org.apache.camel.component.plc4x;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.PropertiesHelper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Reproducer for CAMEL-24171: the reporter observed that, on a route shaped like
 *
 * <pre>
 * from(timer("s7").period(5000))
 *         .pollEnrich(plc4x("s7://localhost:102").autoReconnect(true).tags(Map.of("var1", "RAW(%DB1.DBX0.0:BOOL)")), 5000)
 * </pre>
 *
 * {@code Plc4XEndpoint#doStart()} runs again on every poll cycle (reloading {@code DefaultPlcDriverManager}), while a
 * structurally identical Modbus route only runs it once.
 * <p>
 * {@link #directGetEndpointCachingProbe()} isolates the actual root cause: it is not specific to S7, pollEnrich, or
 * camel-plc4x at all. Any endpoint URI whose query contains a {@code %} character (as S7 tag addresses like
 * {@code RAW(%DB1.DBX0.0:BOOL)} do, unlike Modbus' {@code RAW(coil:1)}) makes {@code CamelContext#getEndpoint(String)}
 * return a brand-new {@link Endpoint} instance on every call instead of the cached singleton, even though
 * {@code getEndpointUri()} prints byte-for-byte identical text both times. The generic, camel-core-level root cause
 * (double-normalization in {@code AbstractCamelContext#getEndpointKey}) is reproduced without any plc4x involvement in
 * {@code org.apache.camel.impl.DefaultEndpointRegistryTest#testGetEndpointIsCachedForUriContainingPercentCharacter} in
 * camel-core. {@link #doStartRunsOnlyOnceEvenThoughBothRoutesArePolledManyTimes()} then shows the real-world
 * consequence through the exact reported route shape: with a S7-style {@code %}-containing tag,
 * {@code Plc4XEndpoint#doStart()} (which recreates {@code DefaultPlcDriverManager}) fires again on every single poll,
 * while the Modbus-style route only runs it once.
 */
public class Plc4XPollEnrichDoStartReproducerTest extends CamelTestSupport {

    final AtomicInteger modbusStartCount = new AtomicInteger();
    final AtomicInteger s7StartCount = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.addComponent("plc4x", new CountingPlc4XComponent());
        return ctx;
    }

    @Test
    public void directGetEndpointCachingProbe() throws Exception {
        String uri = "plc4x:mock:s7-probe?autoReconnect=true&tag.var1=RAW(%DB1.DBX0.0:BOOL)";

        Endpoint first = context.getEndpoint(uri);
        Endpoint second = context.getEndpoint(uri);

        assertThat("getEndpointUri() must be identical between the two lookups (it is: normalization is stable)",
                second.getEndpointUri(), is(first.getEndpointUri()));
        assertThat("calling getEndpoint(uri) twice with the identical uri string must return the same cached instance",
                second, sameInstance(first));
    }

    @Test
    public void doStartRunsOnlyOnceEvenThoughBothRoutesArePolledManyTimes() throws Exception {
        MockEndpoint modbusResult = getMockEndpoint("mock:modbusResult");
        MockEndpoint s7Result = getMockEndpoint("mock:s7Result");
        modbusResult.expectedMinimumMessageCount(10);
        s7Result.expectedMinimumMessageCount(10);

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        assertThat("Plc4XEndpoint#doStart() must only run once per endpoint for the modbus-shaped route",
                modbusStartCount.get(), is(1));
        assertThat("Plc4XEndpoint#doStart() must only run once per endpoint for the s7-shaped route",
                s7StartCount.get(), is(1));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:modbus?period=20")
                        .pollEnrich("plc4x:mock:modbus-like?autoReconnect=true&tag.coil-1=RAW(coil:1)", 5000)
                        .routeId("modbus")
                        .to("mock:modbusResult");

                from("timer:s7?period=20")
                        .pollEnrich("plc4x:mock:s7-like?autoReconnect=true&tag.var1=RAW(%DB1.DBX0.0:BOOL)", 5000)
                        .routeId("s7")
                        .to("mock:s7Result");
            }
        };
    }

    /**
     * Duplicates {@link Plc4XComponent#createEndpoint} but hands back an endpoint whose doStart() is countable, without
     * touching production code.
     */
    private class CountingPlc4XComponent extends Plc4XComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            AtomicInteger counter = uri.contains("s7-like") ? s7StartCount : modbusStartCount;
            Plc4XEndpoint endpoint = new Plc4XEndpoint(uri, this) {
                @Override
                protected void doStart() throws Exception {
                    counter.incrementAndGet();
                    super.doStart();
                }
            };

            Map<String, String> tags = getAndRemoveOrResolveReferenceParameter(parameters, "tags", Map.class);
            Map<String, Object> map = PropertiesHelper.extractProperties(parameters, "tag.");
            if (map != null) {
                if (tags == null) {
                    tags = new LinkedHashMap<>();
                }
                for (Map.Entry<String, Object> me : map.entrySet()) {
                    tags.put(me.getKey(), me.getValue().toString());
                }
            }
            if (tags != null) {
                endpoint.setTags(tags);
            }

            String trigger = getAndRemoveOrResolveReferenceParameter(parameters, "trigger", String.class);
            if (trigger != null) {
                endpoint.setTrigger(trigger);
            }
            Integer period = getAndRemoveOrResolveReferenceParameter(parameters, "period", Integer.class);
            if (period != null) {
                endpoint.setPeriod(period);
            }
            setProperties(endpoint, parameters);
            return endpoint;
        }
    }

}
