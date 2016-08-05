/**
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

import java.util.concurrent.TimeUnit;

import javax.enterprise.event.Observes;

import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.management.event.CamelContextStartingEvent;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.cdi.CamelCdiRunner;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Verifier;
import org.junit.runner.RunWith;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;

@RunWith(CamelCdiRunner.class)
public class AdviceTest {

    @ClassRule
    public static MessageVerifier verifier = new MessageVerifier();

    void advice(@Observes CamelContextStartingEvent event,
                @Uri("mock:messages") MockEndpoint messages,
                ModelCamelContext context) throws Exception {
        messages.expectedMessageCount(2);
        messages.expectedBodiesReceived("Hello", "Bye");

        verifier.messages = messages;

        context.getRouteDefinition("route")
            .adviceWith(context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() {
                    weaveAddLast().to("mock:messages");
                }
            });
    }

    @Test
    public void test() {
    }

    private static class MessageVerifier extends Verifier {

        MockEndpoint messages;

        @Override
        protected void verify() throws InterruptedException {
            assertIsSatisfied(2L, TimeUnit.SECONDS, messages);
        }
    }
}
