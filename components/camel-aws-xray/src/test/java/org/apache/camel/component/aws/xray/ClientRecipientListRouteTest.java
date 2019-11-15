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
package org.apache.camel.component.aws.xray;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

public class ClientRecipientListRouteTest extends CamelAwsXRayTestSupport {

    public ClientRecipientListRouteTest() {
        super(
            TestDataBuilder.createTrace().inRandomOrder()
                .withSegment(TestDataBuilder.createSegment("start")
                    .withSubsegment(TestDataBuilder.createSubsegment("seda:a"))
                    .withSubsegment(TestDataBuilder.createSubsegment("seda:b"))
                    .withSubsegment(TestDataBuilder.createSubsegment("seda:c"))
                )
                .withSegment(TestDataBuilder.createSegment("a"))
                .withSegment(TestDataBuilder.createSegment("b"))
                .withSegment(TestDataBuilder.createSegment("c"))
        );
    }

    @Test
    public void testRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(7).create();

        template.requestBody("direct:start", "Hello");

        assertThat("Not all exchanges were fully processed",
                notify.matches(5, TimeUnit.SECONDS), is(equalTo(true)));

        verify();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("start")
                    .recipientList(constant("seda:a,seda:b,seda:c"));

                from("seda:a").routeId("a")
                    .log("routing at ${routeId}");

                from("seda:b").routeId("b")
                    .log("routing at ${routeId}")
                    .delay(simple("${random(1000,2000)}"));

                from("seda:c").routeId("c")
                    .log("routing at ${routeId}")
                    .delay(simple("${random(0,100)}"));
            }
        };
    }
}
