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

import java.util.Arrays;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CsvUnmarshalStreamSpringTest extends CamelSpringTestSupport {

    private static final String CSV_SAMPLE = "A,B,C\r1,2,3\rone,two,three";

    @EndpointInject("mock:line")
    private MockEndpoint line;

    @Test
    public void testCsvUnMarshal() throws Exception {
        line.expectedMessageCount(3);

        template.sendBody("direct:start", CSV_SAMPLE);

        assertMockEndpointsSatisfied();

        List body1 = line.getExchanges().get(0).getIn().getBody(List.class);
        List body2 = line.getExchanges().get(1).getIn().getBody(List.class);
        List body3 = line.getExchanges().get(2).getIn().getBody(List.class);
        assertEquals(Arrays.asList("A", "B", "C"), body1);
        assertEquals(Arrays.asList("1", "2", "3"), body2);
        assertEquals(Arrays.asList("one", "two", "three"), body3);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/csv/CsvUnmarshalStreamSpringTest-context.xml");
    }
}
