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
package org.apache.camel.component.xslt;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

class XsltFromFileExceptionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testXsltFromFileExceptionOk() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(0);

        // Write file BEFORE starting the route so the file consumer
        // picks it up on its very first poll — eliminates the race between
        // file write and consumer poll scheduling under CI load
        Files.writeString(testFile("hello.xml"), "<hello>world!</hello>");

        context.addRoutes(createRouteBuilder());
        context.start();

        assertMockEndpointsSatisfied();

        // File move happens asynchronously after route processing completes
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertFileNotExists(testFile("hello.xml"));
                    assertFileExists(testFile("ok/hello.xml"));
                });
    }

    @Test
    void testXsltFromFileExceptionFail() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        // Write malformed XML (the last tag is not ended properly) BEFORE
        // starting the route so the file consumer picks it up on first poll
        Files.writeString(testFile("hello2.xml"), "<hello>world!</hello");

        context.addRoutes(createRouteBuilder());
        context.start();

        assertMockEndpointsSatisfied();

        // File move happens asynchronously after route processing completes
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertFileNotExists(testFile("hello2.xml"));
                    assertFileExists(testFile("error/hello2.xml"));
                });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?moveFailed=error&move=ok&initialDelay=0&delay=10")).onException(Exception.class)
                        .to("mock:error").end()
                        .to("xslt:org/apache/camel/component/xslt/example.xsl").to("mock:result");
            }
        };
    }
}
