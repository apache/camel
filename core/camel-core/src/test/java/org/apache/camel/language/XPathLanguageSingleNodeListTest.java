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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Tests new converters added to XmlConverters to make Camel intelligent when
 * needing to convert a NodeList of length 1 into a Document or a Node.
 */
public class XPathLanguageSingleNodeListTest extends ContextTestSupport {

    private static final String XML_INPUT_SINGLE = "<root><name>Raul</name><surname>Kripalani</surname></root>";
    private static final String XML_INPUT_MULTIPLE = "<root><name>Raul</name><name>Raul</name><surname>Kripalani</surname></root>";

    /**
     * A single node XPath selection that internally returns a DTMNodeList of
     * length 1 can now be automatically converted to a Document/Node.
     * 
     * @throws Exception
     */
    @Test
    public void testSingleNodeList() throws Exception {
        getMockEndpoint("mock:found").expectedMessageCount(1);
        getMockEndpoint("mock:found").setResultWaitTime(500);
        getMockEndpoint("mock:notfound").expectedMessageCount(0);
        getMockEndpoint("mock:notfound").setResultWaitTime(500);

        template.requestBody("direct:doTest", XML_INPUT_SINGLE, String.class);
        assertMockEndpointsSatisfied();

    }

    /**
     * Regression test to ensure that a NodeList of length > 1 is not processed
     * by the new converters.
     * 
     * @throws Exception
     */
    @Test
    public void testMultipleNodeList() throws Exception {
        getMockEndpoint("mock:found").expectedMessageCount(0);
        getMockEndpoint("mock:found").setResultWaitTime(500);
        getMockEndpoint("mock:notfound").expectedMessageCount(0);
        getMockEndpoint("mock:notfound").setResultWaitTime(500);

        try {
            template.requestBody("direct:doTest", XML_INPUT_MULTIPLE, String.class);
            fail("NoTypeConversionAvailableException expected");
        } catch (CamelExecutionException ex) {
            assertEquals(RuntimeCamelException.class, ex.getCause().getClass());
            assertEquals(NoTypeConversionAvailableException.class, ex.getCause().getCause().getClass());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:doTest").transform().xpath("/root/name").choice().when().xpath("/name").to("mock:found").otherwise().to("mock:notfound");
            }
        };
    }

}
