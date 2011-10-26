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
package org.apache.camel.component.xquery;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 *
 */
public class XQueryFromFileExceptionTest extends CamelTestSupport {

    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/xquery");
        super.setUp();
    }

    @Test
    public void testXQueryFromFileExceptionOk() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(0);

        String body = "<person user='James'><firstName>James</firstName>"
                          + "<lastName>Strachan</lastName><city>London</city></person>";
        template.sendBodyAndHeader("file:target/xquery", body, Exchange.FILE_NAME, "hello.xml");

        assertMockEndpointsSatisfied();

        Thread.sleep(500);

        File file = new File("target/xquery/hello.xml");
        assertFalse("File should not exists " + file, file.exists());

        file = new File("target/xquery/ok/hello.xml");
        assertTrue("File should exists " + file, file.exists());
    }

    @Test
    public void testXQueryFromFileExceptionFail() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        // the last tag is not ended properly
        String body = "<person user='James'><firstName>James</firstName>"
                          + "<lastName>Strachan</lastName><city>London</city></person";
        template.sendBodyAndHeader("file:target/xquery", body, Exchange.FILE_NAME, "hello2.xml");

        assertMockEndpointsSatisfied();

        Thread.sleep(500);

        File file = new File("target/xquery/hello2.xml");
        assertFalse("File should not exists " + file, file.exists());

        file = new File("target/xquery/error/hello2.xml");
        assertTrue("File should exists " + file, file.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/xquery?moveFailed=error&move=ok")
                    .onException(Exception.class)
                        .to("mock:error")
                    .end()
                    .to("xquery:org/apache/camel/component/xquery/myTransform.xquery")
                    .to("mock:result");
            }
        };
    }

}
