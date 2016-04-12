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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Spring based integration test for the <code>CsvDataFormat</code> demonstrating the usage of
 * the <tt>autogenColumns</tt>, <tt>configRef</tt> and <tt>strategyRef</tt> options.
 */

public class CsvMarshalAutogenColumnsSpringQuoteModeTest extends CamelSpringTestSupport {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;

    @EndpointInject(uri = "mock:result2")
    private MockEndpoint result2;

    @Test
    public void retrieveColumnsWithAutogenColumnsFalseAndItemColumnsSet() throws Exception {
        result.expectedMessageCount(1);

        template.sendBody("direct:start", createBody());

        result.assertIsSatisfied();

        String body = result.getReceivedExchanges().get(0).getIn().getBody(String.class);
        String[] lines = body.split(LS);
        assertEquals(2, lines.length);
        assertEquals("\"Camel in Action\"", lines[0].trim());
        assertEquals("\"ActiveMQ in Action\"", lines[1].trim());
    }

    @Test
    public void retrieveColumnsWithAutogenColumnsFalseAndOrderIdAmountColumnsSet() throws Exception {
        result2.expectedMessageCount(1);

        template.sendBody("direct:start2", createBody());

        result2.assertIsSatisfied();

        String body = result2.getReceivedExchanges().get(0).getIn().getBody(String.class);
        String[] lines = body.split(LS);
        assertEquals(2, lines.length);
        assertEquals("123|1", lines[0].trim());
        assertEquals("124|2", lines[1].trim());
    }

    private static List<Map<String, Object>> createBody() {
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();

        Map<String, Object> row1 = new LinkedHashMap<String, Object>();
        row1.put("orderId", 123);
        row1.put("item", "Camel in Action");
        row1.put("amount", 1);
        data.add(row1);

        Map<String, Object> row2 = new LinkedHashMap<String, Object>();
        row2.put("orderId", 124);
        row2.put("item", "ActiveMQ in Action");
        row2.put("amount", 2);
        data.add(row2);
        return data;
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/csv/CsvMarshalAutogenColumnsSpringQuoteModeTest-context.xml");
    }
}