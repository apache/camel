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
package org.apache.camel.dataformat.csv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class CsvRouteTest extends ContextTestSupport {
    private static final transient Log LOG = LogFactory.getLog(CsvRouteTest.class);

    public void testSendMessage() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        // START SNIPPET: marshalInput
        Map body = new HashMap();
        body.put("foo", "abc");
        body.put("bar", 123);
        // END SNIPPET: marshalInput
        template.sendBody("direct:start", body);

        resultEndpoint.assertIsSatisfied();
        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            Message in = exchange.getIn();
            String text = in.getBody(String.class);

            log.debug("Received " + text);
            assertNotNull("Should be able to convert received body to a string", text);
            
            // order is not guaranteed with a Map (which was passed in before)
            // so we need to check for both combinations
            assertTrue("Text body has wrong value.", "abc,123".equals(text.trim()) 
                       || "123,abc".equals(text.trim()));
        }
    }

    public void testUnMarshal() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:daltons");
        endpoint.expectedMessageCount(1);
        endpoint.assertIsSatisfied();
        Exchange exchange = endpoint.getExchanges().get(0);
        // START SNIPPET : unmarshalResult
        List<List<String>> data = (List<List<String>>) exchange.getIn().getBody();
        for (List<String> line : data) {
            LOG.debug(String.format("%s has an IQ of %s and is currently %s",
                                    line.get(0), line.get(1), line.get(2)));
        }
        // END SNIPPET : unmarshalResult
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: marshalRoute
                from("direct:start").
                    marshal().csv().
                    to("mock:result");
                // END SNIPPET: marshalRoute

                // START SNIPPET: unmarshalRoute
                from("file:src/test/resources/daltons.csv?noop=true").
                    unmarshal().csv().
                    to("mock:daltons");
                // END SNIPPET: unmarshalRoute
            }
        };
    }
}
