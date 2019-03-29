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
package org.apache.camel.dataformat.tagsoup;

import java.io.File;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class TidyMarkupDataFormatAsDomNodeTest extends CamelTestSupport {
    
    @Test
    public void testUnMarshalToStringOfXml() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(2);

        /*
         * each of these files has a <p>TidyMarkupNode section. (no closing tag)
         * 
         * See the route below, we send the tidyMarkup to xpath and boolean that out.
         */
        String badHtml = TidyMarkupTestSupport.loadFileAsString(new File(
                "src/test/resources/org/apache/camel/dataformat/tagsoup/testfile1.html"));
        String evilHtml = TidyMarkupTestSupport.loadFileAsString(new File(
                "src/test/resources/org/apache/camel/dataformat/tagsoup/testfile2-evilHtml.html"));

        template.sendBody("direct:start", badHtml);
        template.sendBody("direct:start", evilHtml);

        resultEndpoint.assertIsSatisfied();
        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            Message in = exchange.getIn();
            String response = in.getBody(String.class);

            log.debug("Received " + response);
            assertNotNull("Should be able to convert received body to a string", response);

            try {
                /*
                 * our route xpaths the existence of our signature "<p>TidyMarkupNode"
                 * but of course, by the xpath time, it is well formed
                 */
                assertTrue(response.equals("true"));
            } catch (Exception e) {

                fail("Failed to convert the resulting String to XML: " + e.getLocalizedMessage());
            }

        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").unmarshal().tidyMarkup().setBody().xpath(
                        "boolean(//p[contains(text(),'TidyMarkupNode')])", String.class).to("mock:result");
            }
        };
    }

}
