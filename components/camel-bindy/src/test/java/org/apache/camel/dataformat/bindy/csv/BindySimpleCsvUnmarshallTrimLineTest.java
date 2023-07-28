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
package org.apache.camel.dataformat.bindy.csv;

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ContextConfiguration
@CamelSpringTest
public class BindySimpleCsvUnmarshallTrimLineTest {

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_MOCK_ERROR = "mock:error";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(URI_DIRECT_START)
    private ProducerTemplate template;

    @EndpointInject(URI_MOCK_RESULT)
    private MockEndpoint result;

    @EndpointInject(URI_MOCK_ERROR)
    private MockEndpoint error;

    private String expected;

    @Test
    @DirtiesContext
    public void testTrimLineFalse() throws Exception {
        expected = "01,Donald Duck  ";

        template.sendBody(expected);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        Cartoon c = result.getExchanges().get(0).getMessage().getBody(Cartoon.class);
        assertNotNull(c);
        assertEquals(1, c.getNo());
        assertEquals("Donald Duck  ", c.getName());
    }

    @Test
    @DirtiesContext
    public void testTrimLineFalseTwo() throws Exception {
        expected = "01,Donald Duck  \r\n02,  Bugs Bunny ";

        template.sendBody(expected);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        List<Cartoon> l = result.getExchanges().get(0).getMessage().getBody(List.class);
        assertEquals(2, l.size());
        Cartoon c = l.get(0);
        assertEquals(1, c.getNo());
        assertEquals("Donald Duck  ", c.getName());

        c = l.get(1);
        assertEquals(2, c.getNo());
        assertEquals("  Bugs Bunny ", c.getName());
    }

    public static class ContextConfig extends RouteBuilder {
        BindyCsvDataFormat camelDataFormat
                = new BindyCsvDataFormat(Cartoon.class);

        @Override
        public void configure() {
            from(URI_DIRECT_START).unmarshal(camelDataFormat).to(URI_MOCK_RESULT);
        }

    }
}
