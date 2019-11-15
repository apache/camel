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

import com.amazonaws.xray.AWSXRay;
import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.InterceptStrategy;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

@Ignore("TODO: investigate for Camel 3.0")
public class EIPTracingTest extends CamelAwsXRayTestSupport {

    public EIPTracingTest() {
        super(
            TestDataBuilder.createTrace()
                .withSegment(TestDataBuilder.createSegment("start")
                    .withSubsegment(TestDataBuilder.createSubsegment("log"))
                    .withSubsegment(TestDataBuilder.createSubsegment("bean"))
                    .withSubsegment(TestDataBuilder.createSubsegment("delay")
                        .withSubsegment(TestDataBuilder.createSubsegment("to")
                            .withSubsegment(TestDataBuilder.createSubsegment("seda:otherRoute"))
                        )
                        .withSubsegment(TestDataBuilder.createSubsegment("to")
                            .withSubsegment(TestDataBuilder.createSubsegment("mock:end"))
                        )
                    )
                    .withAnnotation("body", "HELLO")
                    .withMetadata("originBody", "Hello")
                )
                .withSegment(TestDataBuilder.createSegment("otherRoute")
                    .withSubsegment(TestDataBuilder.createSubsegment("log"))
                    .withSubsegment(TestDataBuilder.createSubsegment("process"))
                    .withSubsegment(TestDataBuilder.createSubsegment("delay"))
                )
        );
    }

    @Override
    protected InterceptStrategy getTracingStrategy() {
        return new EIPTracingStrategy();
    }

    @Test
    public void testRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).create();

        MockEndpoint mockEndpoint = context.getEndpoint("mock:end", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.expectedBodiesReceived("HELLO");
        mockEndpoint.expectedHeaderReceived("TEST", "done");

        template.requestBody("direct:start", "Hello");

        assertThat("Not all exchanges were fully processed",
                notify.matches(5, TimeUnit.SECONDS), is(equalTo(true)));

        mockEndpoint.assertIsSatisfied();

        verify();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("start")
                    .log("start has been called")
                    .bean(TraceBean.class)
                    .delay(simple("${random(1000,2000)}"))
                    .to("seda:otherRoute")
                    .to("mock:end");

                from("seda:otherRoute").routeId("otherRoute")
                    .log("otherRoute has been called")
                    .process(new CustomProcessor())
                    .delay(simple("${random(0,500)}"));
            }
        };
    }

    @XRayTrace
    public static class TraceBean {

        @Handler
        public String convertBocyToUpperCase(@Body String body) {
            String converted = body.toUpperCase();
            AWSXRay.getCurrentSegment().putAnnotation("body", converted);
            AWSXRay.getCurrentSegment().putMetadata("originBody", body);
            return converted;
        }
    }

    @XRayTrace(metricName = "processor")
    public static class CustomProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setHeader("TEST", "done");
        }
    }
}
