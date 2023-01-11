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
package org.apache.camel.test.cdi;

import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.Uri;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(CamelCdiExtension.class)
class CamelCdiNotifyBuilderTest {

    @Inject
    CamelContext context;

    @Inject
    @Uri("direct:in")
    ProducerTemplate producer;

    @Test
    void test() throws Exception {
        String expected = "Hello Camel";
        NotifyBuilder notify = new NotifyBuilder(context)
                .whenCompleted(1)
                .wereSentTo("mock:out")
                .whenBodiesDone(expected)
                .create();

        producer.sendBody(expected);

        assertTrue(
                notify.matches(20, TimeUnit.SECONDS), "1 message should be completed");
    }

    static class TestRoute extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:in").routeId("test").to("mock:out");
        }
    }
}
