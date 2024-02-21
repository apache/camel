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
package org.apache.camel.component.caffeine.processor.idempotent;

import java.util.UUID;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class CaffeineIdempotentRepositoryWithSplitTest extends CamelTestSupport {

    private CaffeineIdempotentRepository repo;

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        repo = new CaffeineIdempotentRepository("test");
    }

    @Test
    public void idempotentTest() throws Exception {
        final int numberUniqueMessages = 100;
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(numberUniqueMessages);

        for (int i = 0; i < numberUniqueMessages; i++) {
            template().sendBody("direct:idempotentRoute", String.valueOf(i));
            if (i > 0) {
                template().sendBody("direct:idempotentRoute", String.valueOf(i - 1));
            }
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:idempotentRoute")
                    .idempotentConsumer(body(),
                            repo)
                        .to("mock:result")
                    .end();
            }
        };
    }

    protected static String generateRandomString() {
        return UUID.randomUUID().toString();
    }
}
