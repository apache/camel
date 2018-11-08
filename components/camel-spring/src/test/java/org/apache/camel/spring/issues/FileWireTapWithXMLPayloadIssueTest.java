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
package org.apache.camel.spring.issues;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class FileWireTapWithXMLPayloadIssueTest extends SpringTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/xmldata");
        super.setUp();

        template.sendBodyAndHeader("file://target/xmldata",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<sample>\n<test>Helloooo</test>\n</sample>", Exchange.FILE_NAME, "hello.xml");
    }

    @Test
    public void testWireTapXpathExpression() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        MockEndpoint tap = getMockEndpoint("mock:wiretap");
        tap.expectedMessageCount(1);

        assertMockEndpointsSatisfied();

        String dataResult = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        String dataTap = tap.getReceivedExchanges().get(0).getIn().getBody(String.class);

        assertEquals(dataResult, dataTap);
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/issues/FileWireTapWithXMLPayloadIssueTest.xml");
    }
}
