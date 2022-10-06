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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

/**
 *
 */
public class FileRouteJmsPreMoveTest extends AbstractJMSTest {

    protected final String componentName = "activemq";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/FileRouteJmsPreMoveTest/inbox");
        deleteDirectory("target/FileRouteJmsPreMoveTest/outbox");
        super.setUp();
    }

    @Test
    public void testPreMove() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedFileExists("target/FileRouteJmsPreMoveTest/outbox/hello.txt", "Hello World");

        template.sendBodyAndHeader("file://target/FileRouteJmsPreMoveTest/inbox", "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file://target/FileRouteJmsPreMoveTest/inbox?preMove=transfer")
                        .to("activemq:queue:FileRouteJmsPreMoveTest");

                from("activemq:queue:FileRouteJmsPreMoveTest")
                        .to("log:outbox")
                        .to("file://target/FileRouteJmsPreMoveTest/outbox")
                        .to("mock:result");
            }
        };
    }
}
