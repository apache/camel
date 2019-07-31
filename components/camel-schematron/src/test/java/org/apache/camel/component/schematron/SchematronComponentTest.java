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
package org.apache.camel.component.schematron;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.schematron.constant.Constants;
import org.apache.camel.component.schematron.util.Utils;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

/**
 * Schematron Component Test.
 */
public class SchematronComponentTest extends CamelTestSupport {


    /**
     * @throws Exception
     */
    @Test
    public void testSendBodyAsString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        String payload = IOUtils.toString(ClassLoader.getSystemResourceAsStream("xml/article-1.xml"));
        template.sendBody("direct:start", payload);
        assertMockEndpointsSatisfied();
        String result = mock.getExchanges().get(0).getIn().getHeader(Constants.VALIDATION_REPORT, String.class);
        assertEquals(0, Integer.valueOf(Utils.evaluate("count(//svrl:failed-assert)", result)).intValue());
        assertEquals(0, Integer.valueOf(Utils.evaluate("count(//svrl:successful-report)", result)).intValue());
    }

    /**
     * @throws Exception
     */
    @Test
    public void testSendBodyAsInputStreamInvalidXML() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        String payload = IOUtils.toString(ClassLoader.getSystemResourceAsStream("xml/article-2.xml"));
        template.sendBody("direct:start", payload);
        assertMockEndpointsSatisfied();
        String result = mock.getExchanges().get(0).getIn().getHeader(Constants.VALIDATION_REPORT, String.class);


        // should throw two assertions because of the missing chapters in the XML.
        assertEquals("A chapter should have a title", Utils.evaluate("//svrl:failed-assert[1]/svrl:text", result));
        assertEquals("A chapter should have a title", Utils.evaluate("//svrl:failed-assert[2]/svrl:text", result));

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("schematron://sch/schematron-1.sch")
                        .to("mock:result");
            }
        };
    }
}
