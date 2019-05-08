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
package org.apache.camel.dataformat.csv;

import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Spring based test for the <code>CsvDataFormat</code> demonstrating the usage of
 * the <tt>useMaps</tt> option.
 */
public class CsvUnmarshalMapLineSpringTest extends CamelSpringTestSupport {
    @EndpointInject("mock:result")
    private MockEndpoint result;

    @SuppressWarnings("unchecked")
    @Test
    public void testCsvUnMarshal() throws Exception {
        result.expectedMessageCount(1);

        // the first line contains the column names which we intend to skip
        template.sendBody("direct:start", "OrderId|Item|Amount\n123|Camel in Action|1\n124|ActiveMQ in Action|2");

        assertMockEndpointsSatisfied();

        List<Map<String, String>> body = result.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals(2, body.size());
        assertEquals("123", body.get(0).get("OrderId"));
        assertEquals("Camel in Action", body.get(0).get("Item"));
        assertEquals("1", body.get(0).get("Amount"));
        assertEquals("124", body.get(1).get("OrderId"));
        assertEquals("ActiveMQ in Action", body.get(1).get("Item"));
        assertEquals("2", body.get(1).get("Amount"));
    }

    @Test
    public void testCsvUnMarshalNoLine() throws Exception {
        result.expectedMessageCount(1);

        // the first and last line we intend to skip
        template.sendBody("direct:start", "OrderId|Item|Amount\n");

        assertMockEndpointsSatisfied();

        List<?> body = result.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals(0, body.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldUseExplicitHeadersInMap() throws Exception {
        result.expectedMessageCount(1);

        template.sendBody("direct:explicitHeader", "123|Camel in Action|1\n124|ActiveMQ in Action|2");

        assertMockEndpointsSatisfied();

        List<Map<String, String>> body = result.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals(2, body.size());
        assertEquals("123", body.get(0).get("MyOrderId"));
        assertEquals("Camel in Action", body.get(0).get("MyItem"));
        assertEquals("1", body.get(0).get("MyAmount"));
        assertEquals("124", body.get(1).get("MyOrderId"));
        assertEquals("ActiveMQ in Action", body.get(1).get("MyItem"));
        assertEquals("2", body.get(1).get("MyAmount"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReplaceHeaderInMap() throws Exception {
        result.expectedMessageCount(1);

        template.sendBody("direct:replaceHeader", "a|b|c\n123|Camel in Action|1\n124|ActiveMQ in Action|2");

        assertMockEndpointsSatisfied();

        List<Map<String, String>> body = result.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals(2, body.size());
        assertEquals("123", body.get(0).get("MyOrderId"));
        assertEquals("Camel in Action", body.get(0).get("MyItem"));
        assertEquals("1", body.get(0).get("MyAmount"));
        assertEquals("124", body.get(1).get("MyOrderId"));
        assertEquals("ActiveMQ in Action", body.get(1).get("MyItem"));
        assertEquals("2", body.get(1).get("MyAmount"));
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/csv/CsvUnmarshalMapLineSpringTest-context.xml");
    }
}
