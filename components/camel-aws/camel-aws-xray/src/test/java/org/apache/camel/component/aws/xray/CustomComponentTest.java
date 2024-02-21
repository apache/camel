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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.xray.bean.ProcessingCamelBean;
import org.apache.camel.component.aws.xray.component.CommonEndpoints;
import org.apache.camel.spi.InterceptStrategy;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.aws.xray.TestDataBuilder.createSegment;
import static org.apache.camel.component.aws.xray.TestDataBuilder.createSubsegment;
import static org.apache.camel.component.aws.xray.TestDataBuilder.createTrace;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

/**
 * This test uses a custom component that will trigger a long-running backing task for certain specific states. The task
 * is forwarded via an asynchronous send to a Camel route which then performs the task, such as an upload or a
 * computation.
 * <p>
 * AWS XRay does monitor the subsegment count per segment and only emits the segment to the local XRay daemon once the
 * segment is closed and its internal count reaches 0. If the segment is closed before the counter reached 0 the segment
 * is not emitted till the last subsegments belonging to that segment got closed.
 * <p>
 * Due to the asynchronous nature of the backing {@link ProcessingCamelBean processing camel bean}, the first request is
 * still in progress when the second request is triggered. As those tasks aren't executed in parallel, AWS XRay does not
 * take notice of the seconds processing Camel bean invocation yet which leads to a premature emit of that segment and
 * thus missing subsegments for the route and bean invocation. This is possible as the count of the segment reached 0
 * when the segment got closed as Camel has not had a chance yet to create the subsegments for the asynchronously
 * executed route and its bean invocation.
 */
public class CustomComponentTest extends CamelAwsXRayTestSupport {

    private static final String START = "seda:start";
    private static final String DELIVERY = "seda:delivery";
    private static final String IN_QUEUE = "seda:inqueue";
    private static final String PERSISTENCE_QUEUE = "seda:persistence-queue";
    private static final String PERSISTING = "seda:persisting";

    public CustomComponentTest() {
        super(
              createTrace().inRandomOrder()
                      .withSegment(createSegment("start")
                              .withSubsegment(createSubsegment(DELIVERY)))
                      .withSegment(createSegment("delivery")
                              .withSubsegment(createSubsegment(CommonEndpoints.RECEIVED)
                                      .withSubsegment(createSubsegment("seda:backingTask"))
                                      .withSubsegment(createSubsegment("seda:backingTask"))
                                      .withMetadata("state", "received"))
                              .withSubsegment(createSubsegment(IN_QUEUE)))
                      .withSegment(createSegment("processing")
                              .withSubsegment(createSubsegment(CommonEndpoints.PROCESSING))
                              .withSubsegment(createSubsegment(PERSISTENCE_QUEUE)))
                      .withSegment(createSegment("wait-for-persisting")
                              .withSubsegment(createSubsegment(CommonEndpoints.PERSISTENCE_QUEUE))
                              .withSubsegment(createSubsegment(PERSISTING)))
                      .withSegment(createSegment("persisting")
                              .withSubsegment(createSubsegment(CommonEndpoints.READY)
                              // not available due to the asynchronous, long-running nature of the processing
                              // bean. If the sleep is commented out in the bean, this subsegments should be
                              // available
                              //                                        .withSubsegment(createSubsegment("backingTask")
                              //                                                .withSubsegment(createSubsegment("bean:ProcessingCamelBean"))
                              //                                        )
                              //                                        .withMetadata("state", "ready")
                              )));
    }

    @Override
    protected InterceptStrategy getTracingStrategy() {
        return new TraceAnnotatedTracingStrategy();
    }

    @Test
    public void testRoute() {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();

        template.requestBody(START, "Hello");

        assertThat("Not all exchanges were fully processed",
                notify.matches(10, TimeUnit.SECONDS), is(equalTo(true)));

        verify();

        assertThat(ProcessingCamelBean.gotInvoked(), is(greaterThan(0)));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() {
                from(START).routeId("start")
                        .log("Starting test")
                        .to(ExchangePattern.InOnly, DELIVERY);

                from(DELIVERY).routeId("delivery")
                        .log("Doing some stuff")
                        .to(CommonEndpoints.RECEIVED)
                        .delay(100)
                        .to(ExchangePattern.InOnly, IN_QUEUE);

                from(IN_QUEUE).routeId("processing")
                        .log("Do some more stuff")
                        .to(CommonEndpoints.PROCESSING)
                        .delay(100)
                        .to(ExchangePattern.InOnly, PERSISTENCE_QUEUE);

                from(PERSISTENCE_QUEUE).routeId("wait-for-persisting")
                        .log("Waiting on available persisting instance")
                        .to(CommonEndpoints.PERSISTENCE_QUEUE)
                        .delay(100)
                        .to(ExchangePattern.InOnly, PERSISTING);

                from(PERSISTING).routeId("persisting")
                        .log("Payload ready for usage")
                        .to(CommonEndpoints.READY)
                        .delay(100)
                        .log("done");

                from("seda:backingTask").routeId("backingTask")
                        .onException(Exception.class)
                            .redeliveryDelay(100L)
                            .onRedelivery((Exchange exchange) -> System.err.println(">> Retrying due to "
                                    + exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getLocalizedMessage()))
                            .logExhausted(true)
                            .handled(true)
                            .logStackTrace(true)
                        .end()

                        .log("routing at ${routeId}")
                        .bean(ProcessingCamelBean.class)
                        .log("processing camel bean invoked");
            }
        };
    }
}
