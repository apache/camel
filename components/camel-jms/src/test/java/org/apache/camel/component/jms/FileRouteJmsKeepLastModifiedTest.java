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
package org.apache.camel.component.jms;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileRouteJmsKeepLastModifiedTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected final String componentName = "activemq";
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    private final File inbox = new File("target/FileRouteJmsKeepLastModifiedTest/inbox/hello.txt");

    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/FileRouteJmsKeepLastModifiedTest/inbox");
        deleteDirectory("target/FileRouteJmsKeepLastModifiedTest/outbox");

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader("file://target/FileRouteJmsKeepLastModifiedTest/inbox", "Hello World", Exchange.FILE_NAME,
                "hello.txt");
    }

    @Test
    public void testKeepLastModified() throws Exception {
        MockEndpoint.assertIsSatisfied(context);
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(inbox::exists);

        File outbox = new File("target/FileRouteJmsKeepLastModifiedTest/outbox/hello.txt");
        assertEquals(inbox.lastModified(), outbox.lastModified(), "Should keep last modified");
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file://target/FileRouteJmsKeepLastModifiedTest/inbox?noop=true")
                        .to("activemq:queue:FileRouteJmsKeepLastModifiedTest");

                from("activemq:queue:FileRouteJmsKeepLastModifiedTest")
                        // just a little delay so the write of the file happens later
                        .delayer(100)
                        .to("file://target/FileRouteJmsKeepLastModifiedTest/outbox?keepLastModified=true")
                        .to("mock:result");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
