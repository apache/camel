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
package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.junit.Before;
import org.junit.Test;

public class FileConvertBodyToUTF8Test extends ContextTestSupport {

    private byte[] body;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/utf8");
        super.setUp();

        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        body = "Hello Thai Elephant \u0E08".getBytes("UTF-8");

        template.sendBodyAndHeader("file://target/data/utf8", body, Exchange.FILE_NAME, "utf8.txt");
    }

    @Test
    public void testFileUTF8() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/utf8?initialDelay=0&delay=10").convertBodyTo(String.class, "UTF-8").to("mock:result");
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();

        byte[] data = mock.getReceivedExchanges().get(0).getIn().getBody(byte[].class);
        boolean same = ObjectHelper.equal(body, data);
        assertTrue("Should be same byte data", same);
    }

}
