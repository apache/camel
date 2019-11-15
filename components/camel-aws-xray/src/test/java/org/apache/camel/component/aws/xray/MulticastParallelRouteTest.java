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

public class MulticastParallelRouteTest extends CamelAwsXRayTestSupport {

    public MulticastParallelRouteTest() {
        super(
            TestDataBuilder.createTrace().inRandomOrder()
                .withSegment(TestDataBuilder.createSegment("start")
                    .withSubsegment(TestDataBuilder.createSubsegment("seda:a"))
                )
                .withSegment(TestDataBuilder.createSegment("a").inRandomOrder()
                    .withSubsegment(TestDataBuilder.createSubsegment("seda:b"))
                    .withSubsegment(TestDataBuilder.createSubsegment("seda:c"))
                )
                .withSegment(TestDataBuilder.createSegment("b"))
                .withSegment(TestDataBuilder.createSegment("c")
                        // disabled by the LogSegmentDecorator (-> .to("log:..."); .log("...") is still working)
                        //.withSubsegment(TestDataBuilder.createSubsegment("log:routing%20at%20$%7BrouteId%7D"))
                )
        );
    }

    @Test
    public void testRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context)
                .from("seda:b").whenDone(1)
                .and()
                .from("seda:c").whenDone(1)
                .create();

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
                    .to("seda:a");

                from("seda:a").routeId("a")
                    .log("routing at ${routeId}")
                    .multicast().parallelProcessing()
                    .to("seda:b", "seda:c")
                    .end()
                    .log("End of routing");

                from("seda:b").routeId("b")
                    .log("routing at ${routeId}")
                    .delay(simple("${random(1000,2000)}"));

                from("seda:c").routeId("c")
                    .to("log:routing at ${routeId}")
                    .delay(simple("${random(0,100)}"));
            }
        };
    }
}
