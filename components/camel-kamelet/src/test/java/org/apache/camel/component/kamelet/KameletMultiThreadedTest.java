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
package org.apache.camel.component.kamelet;

import java.util.concurrent.CountDownLatch;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.kamelet.Kamelet.templateToRoute;

public class KameletMultiThreadedTest extends CamelTestSupport {

    @Test
    public void createSameKameletTwiceInParallel_KameletConsumerNotAvailableExceptionThrown() throws InterruptedException {
        var latch = new CountDownLatch(2);
        context.addRouteTemplateDefinitionConverter("*", (in, parameters) -> {
            try {
                return templateToRoute(in, parameters);
            } finally {
                latch.countDown();
                latch.await();
            }
        });
        getMockEndpoint("mock:foo").expectedMessageCount(2);

        template.sendBody("seda:route", null);
        template.requestBody("seda:route", ((Object) null));

        MockEndpoint.assertIsSatisfied(context);
    }

    // **********************************************
    //
    // test set-up
    //
    // **********************************************

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:route?concurrentConsumers=2")
                        .toD("kamelet:-");

                routeTemplate("-"). // This is a workaround for "*" to be iterated before templateId at org.apache.camel.impl.DefaultModel#addRouteFromTemplate (line 460)
                        from("kamelet:source")
                        .to("mock:foo");
            }
        };
    }
}
