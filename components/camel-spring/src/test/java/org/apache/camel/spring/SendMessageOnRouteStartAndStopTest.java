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
package org.apache.camel.spring;

import java.io.File;

import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 */
public class SendMessageOnRouteStartAndStopTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        deleteDirectory("target/startandstop");

        return new ClassPathXmlApplicationContext("org/apache/camel/spring/SendMessageOnRouteStartAndStopTest.xml");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // the event notifier should have send a message to the file endpoint
        // so the start file is created on startup
        File start = new File("target/startandstop/start.txt");
        assertTrue("Start file should exist on startup", start.exists());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        // the event notifier should have send a message to the file endpoint
        // so the stop file is created on shutdown
        File start = new File("target/startandstop/stop.txt");
        assertTrue("Stop file should exist on shutdown", start.exists());
    }

    public void testSendMessageOnStartupAndStop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // this is just to see that the route also works
        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
