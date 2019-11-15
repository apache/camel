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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

public class TwoServiceTest extends CamelAwsXRayTestSupport {

    public TwoServiceTest() {
        super(
            TestDataBuilder.createTrace().inRandomOrder()
                .withSegment(TestDataBuilder.createSegment("ServiceA")
                    .withSubsegment(TestDataBuilder.createSubsegment("direct:ServiceB"))
                )
                .withSegment(TestDataBuilder.createSegment("ServiceB"))
        );
    }

    @Test
    public void testRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        template.requestBody("direct:ServiceA", "Hello");

        assertThat("Not all exchanges were fully processed",
                notify.matches(10, TimeUnit.SECONDS), is(equalTo(true)));
        verify();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:ServiceA").routeId("ServiceA")
                    .log("ServiceA has been called")
                    .delay(simple("${random(1000,2000)}"))
                    .to("direct:ServiceB");

                from("direct:ServiceB").routeId("ServiceB")
                    .log("ServiceB has been called")
                    .delay(simple("${random(0,500)}"));
            }
        };
    }
}
