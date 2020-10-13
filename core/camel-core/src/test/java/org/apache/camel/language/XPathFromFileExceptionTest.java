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
package org.apache.camel.language;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class XPathFromFileExceptionTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data/xpath");
        super.setUp();
    }

    @Test
    public void testXPathFromFileExceptionOk() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(0);

        template.sendBodyAndHeader("file:target/data/xpath", "<hello>world!</hello>", Exchange.FILE_NAME, "hello.xml");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        File file = new File("target/data/xpath/hello.xml");
        assertFalse(file.exists(), "File should not exists " + file);

        file = new File("target/data/xpath/ok/hello.xml");
        assertTrue(file.exists(), "File should exists " + file);
    }

    @Test
    public void testXPathFromFileExceptionFail() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        // the last tag is not ended properly
        template.sendBodyAndHeader("file:target/data/xpath", "<hello>world!</hello", Exchange.FILE_NAME, "hello2.xml");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        File file = new File("target/data/xpath/hello2.xml");
        assertFalse(file.exists(), "File should not exists " + file);

        file = new File("target/data/xpath/error/hello2.xml");
        assertTrue(file.exists(), "File should exists " + file);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/xpath?initialDelay=0&delay=10&moveFailed=error&move=ok").onException(Exception.class)
                        .to("mock:error").end().choice().when().xpath("/hello")
                        .to("mock:result").end();
            }
        };
    }
}
