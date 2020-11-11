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
public class XsltFromFileExceptionTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data/xslt");
        super.setUp();
    }

    @Test
    public void testXsltFromFileExceptionOk() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(0);

        template.sendBodyAndHeader("file:target/data/xslt", "<hello>world!</hello>", Exchange.FILE_NAME, "hello.xml");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        File file = new File("target/data/xslt/hello.xml");
        assertFalse(file.exists(), "File should not exists " + file);

        file = new File("target/data/xslt/ok/hello.xml");
        assertTrue(file.exists(), "File should exists " + file);
    }

    @Test
    public void testXsltFromFileExceptionFail() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        // the last tag is not ended properly
        template.sendBodyAndHeader("file:target/data/xslt", "<hello>world!</hello", Exchange.FILE_NAME, "hello2.xml");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        File file = new File("target/data/xslt/hello2.xml");
        assertFalse(file.exists(), "File should not exists " + file);

        file = new File("target/data/xslt/error/hello2.xml");
        assertTrue(file.exists(), "File should exists " + file);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/xslt?moveFailed=error&move=ok&initialDelay=0&delay=10").onException(Exception.class)
                        .to("mock:error").end()
                        .to("xslt:org/apache/camel/component/xslt/example.xsl").to("mock:result");
            }
        };
    }
}
