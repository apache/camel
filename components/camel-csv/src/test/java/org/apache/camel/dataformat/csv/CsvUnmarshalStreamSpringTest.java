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

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CsvUnmarshalStreamSpringTest extends CamelSpringTestSupport {

    public static final String MESSAGE = "message";

    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;

    @Test
    public void testCsvUnMarshal() throws Exception {
        result.expectedMessageCount(1);

        template.sendBody("direct:start", MESSAGE + "\n");

        assertMockEndpointsSatisfied();

        Iterator<?> body = result.getReceivedExchanges().get(0).getIn().getBody(Iterator.class);
        Iterator iterator = assertIsInstanceOf(Iterator.class, body);
        assertTrue(iterator.hasNext());
        assertEquals(Arrays.asList(MESSAGE), iterator.next());
        assertFalse(iterator.hasNext());
        try {
            iterator.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/csv/CsvUnmarshalStreamSpringTest-context.xml");
    }
}
