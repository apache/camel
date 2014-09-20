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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Marshal tests with List objects.
 */
public class MarshalListTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    MockEndpoint mock;

    @Test
    public void testMarshalList() throws Exception {
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("<?xml version='1.0' encoding='ISO-8859-1'?>"
            + "<list><string>Hello World</string></list>");

        List<String> body = new ArrayList<String>();
        body.add("Hello World");

        template.sendBodyAndProperty("direct:in", body, Exchange.CHARSET_NAME, "ISO-8859-1");

        mock.assertIsSatisfied();
    }

    @Test
    public void testMarshalListWithMap() throws Exception {
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(
            "<?xml version='1.0' encoding='UTF-8'?><list><map><entry><string>city</string>"
                + "<string>London\u0E08</string></entry></map></list>");

        List<Map<Object, String>> body = new ArrayList<Map<Object, String>>();
        Map<Object, String> row = new HashMap<Object, String>();
        row.put("city", "London\u0E08");
        body.add(row);

        template.sendBodyAndProperty("direct:in", body, Exchange.CHARSET_NAME, "UTF-8");

        mock.assertIsSatisfied();
    }

    @Test
    public void testSetEncodingOnXstream() throws Exception {
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(
            "<?xml version='1.0' encoding='UTF-8'?><list><map><entry><string>city</string>"
                + "<string>London\u0E08</string></entry></map></list>");

        List<Map<Object, String>> body = new ArrayList<Map<Object, String>>();
        Map<Object, String> row = new HashMap<Object, String>();
        row.put("city", "London\u0E08");
        body.add(row);

        template.sendBody("direct:in-UTF-8", body);

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").marshal().xstream().to(mock);
                from("direct:in-UTF-8").marshal().xstream("UTF-8").to(mock);
            }
        };
    }

}
