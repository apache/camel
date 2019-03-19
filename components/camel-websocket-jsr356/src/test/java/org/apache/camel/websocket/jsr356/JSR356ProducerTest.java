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
package org.apache.camel.websocket.jsr356;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import static java.util.Collections.singletonList;

import javax.enterprise.context.Dependent;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class JSR356ProducerTest extends CamelTestSupport {
    @Rule
    public final MeecrowaveRule servlet = new MeecrowaveRule(new Meecrowave.Builder() {
        {
            randomHttpPort();
            setScanningPackageIncludes("org.apache.camel.websocket.jsr356.JSR356ProducerTest$"); 
        }
    }, "");

    @Rule
    public final TestName testName = new TestName();

    @Produce(uri = "direct:ensureServerModeSendsProperly")
    private ProducerTemplate serverProducer;

    @Ignore
    @Test
    public void ensureServerModeSendsProperly() throws Exception {
        final String body = getClass().getName() + "#" + testName.getMethodName();
        serverProducer.sendBody(body);
        ExistingServerEndpoint.self.latch.await();
        assertEquals(singletonList(body), ExistingServerEndpoint.self.messages);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:ensureServerModeSendsProperly").id("camel_consumer_acts_as_client").convertBodyTo(String.class)
                    .to("websocket-jsr356://ws://localhost:" + servlet.getConfiguration().getHttpPort() + "/existingserver");
            }
        };
    }

    @Dependent
    @ServerEndpoint("/existingserver")
    public static class ExistingServerEndpoint {
        private static ExistingServerEndpoint self;

        private final Collection<String> messages = new ArrayList<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        @OnOpen
        public void onOpen(final Session session) {
            self = this;
        }

        @OnMessage
        public synchronized void onMessage(final String message) {
            messages.add(message);
            latch.countDown();
        }
    }
}
