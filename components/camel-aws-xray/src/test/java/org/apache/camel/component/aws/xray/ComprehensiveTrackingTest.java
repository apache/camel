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

import org.apache.camel.Handler;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.xray.bean.SomeBean;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

public class ComprehensiveTrackingTest extends CamelAwsXRayTestSupport {

    private InvokeChecker invokeChecker = new InvokeChecker();

    public ComprehensiveTrackingTest() {
        super(
                TestDataBuilder.createTrace().inRandomOrder()
                        .withSegment(TestDataBuilder.createSegment("start").inRandomOrder()
                                .withSubsegment(TestDataBuilder.createSubsegment("seda:d"))
                                .withSubsegment(TestDataBuilder.createSubsegment("direct:a"))
                        )
                        .withSegment(TestDataBuilder.createSegment("a")
                                        .withSubsegment(TestDataBuilder.createSubsegment("seda:b"))
                                        .withSubsegment(TestDataBuilder.createSubsegment("seda:c"))
                                // no tracing of the invoke checker bean as it wasn't annotated with
                                // @XRayTrace
                        )
                        .withSegment(TestDataBuilder.createSegment("b"))
                        .withSegment(TestDataBuilder.createSegment("c")
                                // disabled by the LogSegmentDecorator (-> .to("log:...");
                                //.withSubsegment(TestDataBuilder.createSubsegment("log:test"))
                        )
                        .withSegment(TestDataBuilder.createSegment("d"))
                        .withSegment(TestDataBuilder.createSegment("test"))
        );
    }

    @Test
    public void testRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).from("seda:test").whenDone(1).create();

        template.requestBody("direct:start", "Hello");

        assertThat("Not all exchanges were fully processed",
                notify.matches(10, TimeUnit.SECONDS), is(equalTo(true)));

        verify();

        assertThat(invokeChecker.gotInvoked(), is(equalTo(true)));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("start")
                        .wireTap("seda:d")
                        .to("direct:a");

                from("direct:a").routeId("a")
                        .log("routing at ${routeId}")
                        .to("seda:b")
                        .delay(2000)
                        .bean(SomeBean.class)
                        .to("seda:c")
                        .log("End of routing");

                from("seda:b").routeId("b")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(1000,2000)}"));

                from("seda:c").routeId("c")
                        .to("log:test")
                        .delay(simple("${random(0,100)}"));

                from("seda:d").routeId("d")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(10,50)}"));

                from("seda:test").routeId("test")
                        .log("Async invoked route ${routeId} with body: ${body}")
                        .bean(invokeChecker)
                        .delay(simple("${random(10,50)}"));
            }
        };
    }

    public static class InvokeChecker {

        private boolean invoked;

        @Handler
        public void invoke() {
            this.invoked = true;
        }

        boolean gotInvoked() {
            return this.invoked;
        }
    }
}
