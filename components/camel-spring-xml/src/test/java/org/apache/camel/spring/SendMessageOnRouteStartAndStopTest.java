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
package org.apache.camel.spring;

import java.nio.file.Path;

import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 */
public class SendMessageOnRouteStartAndStopTest extends SpringTestSupport {

    @TempDir
    private static Path testDirectory;

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/SendMessageOnRouteStartAndStopTest.xml");
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        // the event notifier should have send a message to the file endpoint
        // so the start file is created on startup
        assertFileExists(testDirectory.resolve("start.txt"));
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        // the event notifier should have send a message to the file endpoint
        // so the stop file is created on shutdown
        assertFileExists(testDirectory.resolve("stop.txt"));
    }

    @Test
    public void testSendMessageOnStartupAndStop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // this is just to see that the route also works
        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public static Path getTestDirectory() {
        return testDirectory;
    }

}
