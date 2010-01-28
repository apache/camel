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
package org.apache.camel.dataformat.xstream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Marhsal tests with List objects.
 */
public class MarshalListTest extends ContextTestSupport {

    public void testMarshalList() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("<?xml version='1.0' encoding='ISO-8859-1'?>"
            + "<list><string>Hello World</string></list>");

        final List<String> body = new ArrayList<String>();
        body.add("Hello World");

        template.send("direct:in", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOnly);
                exchange.getIn().setBody(body);
                exchange.setProperty(Exchange.CHARSET_NAME, "ISO-8859-1");
            }
        });
       

        mock.assertIsSatisfied();
    }

    public void testMarshalListWithMap() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(
            "<?xml version='1.0' encoding='UTF-8'?><list><map><entry><string>city</string>"
                + "<string>London\u0E08</string></entry></map></list>");

        final List<Map<Object, String>> body = new ArrayList<Map<Object, String>>();
        Map<Object, String> row = new HashMap<Object, String>();
        row.put("city", "London\u0E08");
        body.add(row);

        template.send("direct:in", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOnly);
                exchange.getIn().setBody(body);
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
            }
        });
        
        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").marshal().xstream().to("mock:result");
            }
        };
    }

}
