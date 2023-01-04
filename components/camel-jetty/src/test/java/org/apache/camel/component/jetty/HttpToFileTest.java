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
package org.apache.camel.component.jetty;

import java.nio.file.Path;
import java.time.Duration;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.TestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit testing demonstrating how to store incoming requests as files and serving a response back.
 */
public class HttpToFileTest extends BaseJettyTest {
    @TempDir
    Path testDirectory;

    @Test
    public void testToJettyAndSaveToFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Object out = template.requestBody("http://localhost:{{port}}/myworld", "Hello World");

        String response = context.getTypeConverter().convertTo(String.class, out);
        assertEquals("We got the file", response, "Response from Jetty");

        MockEndpoint.assertIsSatisfied(context);

        // give file some time to save
        Awaitility.await()
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertFileExists(testDirectory.resolve("hello.txt"), "Hello World"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // put the incoming data on the seda queue and return a fixed
                // response that we got the file
                from("jetty:http://localhost:{{port}}/myworld").convertBodyTo(String.class).to("seda:in")
                        .setBody(constant("We got the file"));

                // store the content from the queue as a file
                from("seda:in").setHeader(Exchange.FILE_NAME, constant("hello.txt")).convertBodyTo(String.class)
                        .to(TestSupport.fileUri(testDirectory)).to("mock:result");
            }
        };
    }

}
