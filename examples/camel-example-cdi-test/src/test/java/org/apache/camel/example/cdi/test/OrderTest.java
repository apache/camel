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
package org.apache.camel.example.cdi.test;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.Uri;
import org.apache.camel.test.cdi.CamelCdiRunner;
import org.apache.camel.test.cdi.Order;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Verifier;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

@RunWith(CamelCdiRunner.class)
public class OrderTest {

    @ClassRule
    public static MessageVerifier verifier = new MessageVerifier();

    @Inject
    @Uri("direct:in")
    private ProducerTemplate producer;

    @Test
    @Order(2)
    public void sendMessageTwo() {
        producer.sendBody("two");
    }

    @Test
    @Order(3)
    public void sendMessageThree() {
        producer.sendBody("three");
    }

    @Test
    @Order(1)
    public void sendMessageOne() {
        producer.sendBody("one");
    }

    static class TestRoute extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:out").process(verifier);
        }
    }

    static class MessageVerifier extends Verifier implements Processor {

        private final List<String> messages = new ArrayList<>();

        @Override
        protected void verify() {
            assertThat("Messages sequence is incorrect!", messages,
                contains("one", "two", "three"));
        }

        @Override
        public void process(Exchange exchange) {
            messages.add(exchange.getIn().getBody(String.class));
        }
    }
}
