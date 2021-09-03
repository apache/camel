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
package org.apache.camel.component.xquery;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertFileExists;
import static org.apache.camel.test.junit5.TestSupport.assertFileNotExists;

/**
 *
 */
public class XQueryFromFileExceptionTest extends CamelTestSupport {

    @Test
    public void testXQueryFromFileExceptionOk() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(0);

        String body = "<person user='James'><firstName>James</firstName>"
                      + "<lastName>Strachan</lastName><city>London</city></person>";
        template.sendBodyAndHeader(fileUri(), body, Exchange.FILE_NAME, "hello.xml");

        assertMockEndpointsSatisfied();

        Thread.sleep(500);

        assertFileNotExists(testFile("hello.xml"));
        assertFileExists(testFile("ok/hello.xml"));
    }

    @Test
    public void testXQueryFromFileExceptionFail() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        // the last tag is not ended properly
        String body = "<person user='James'><firstName>James</firstName>"
                      + "<lastName>Strachan</lastName><city>London</city></person";
        template.sendBodyAndHeader(fileUri(), body, Exchange.FILE_NAME, "hello2.xml");

        assertMockEndpointsSatisfied();

        Thread.sleep(500);

        assertFileNotExists(testFile("hello2.xml"));
        assertFileExists(testFile("error/hello2.xml"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("?moveFailed=error&move=ok"))
                        .onException(Exception.class)
                        .to("mock:error")
                        .end()
                        .to("xquery:org/apache/camel/component/xquery/myTransform.xquery")
                        .to("mock:result");
            }
        };
    }

}
