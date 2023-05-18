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
package org.apache.camel.observation;

import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.trace.SpanKind;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tags({ @Tag("not-parallel") })
class RouteConcurrentTest extends CamelMicrometerObservationTestSupport {

    private static SpanTestData[] testdata = {
            new SpanTestData().setLabel("seda:foo server").setUri("seda://foo").setOperation("foo")
                    .setKind(SpanKind.CLIENT),
            new SpanTestData().setLabel("seda:bar server").setUri("seda://bar").setOperation("bar")
                    .setParentId(2)
                    .setKind(SpanKind.CLIENT),
            new SpanTestData().setLabel("seda:foo server").setUri("seda://foo?concurrentConsumers=5").setOperation("foo")
                    .setKind(SpanKind.SERVER)
                    .setParentId(0),
            new SpanTestData().setLabel("seda:bar server").setUri("seda://bar?concurrentConsumers=5").setOperation("bar")
                    .setKind(SpanKind.SERVER)
                    .setParentId(1),
    };

    RouteConcurrentTest() {
        super(testdata);
    }

    @Test
    void testSingleInvocationsOfRoute() {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).create();

        template.sendBody("seda:foo", "Hello World");

        assertTrue(notify.matches(30, TimeUnit.SECONDS));

        verify();
    }

    @Test
    void testConcurrentInvocationsOfRoute() {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(10).create();

        for (int i = 0; i < 5; i++) {
            template.sendBody("seda:foo", "Hello World");
        }

        assertTrue(notify.matches(30, TimeUnit.SECONDS));

        verifyTraceSpanNumbers(5, testdata.length);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo?concurrentConsumers=5").routeId("foo")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(1000,2000)}"))
                        .to("seda:bar");

                from("seda:bar?concurrentConsumers=5").routeId("bar")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(0,500)}"));
            }
        };
    }
}
