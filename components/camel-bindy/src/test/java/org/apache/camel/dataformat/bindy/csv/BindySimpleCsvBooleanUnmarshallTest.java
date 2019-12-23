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
import org.apache.camel.dataformat.bindy.model.simple.bool.BooleanExample;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@ContextConfiguration
public class BindySimpleCsvBooleanUnmarshallTest extends AbstractJUnit4SpringContextTests {

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
    
    @SuppressWarnings("unchecked")
    @Test
    @DirtiesContext
    public void testUnMarshallMessageWithBoolean() throws Exception {
       
        // We suppress the firstName field of the first record
        expected = "andrew,true\r\n" + "andrew,false\r\n";

        template.sendBody(expected);

        List<BooleanExample> examples = (List<BooleanExample>)result.getExchanges().get(0).getIn().getBody();
        
        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        
        assertFalse(examples.get(0).getName().isEmpty());
        assertEquals(examples.get(0).getName(), "andrew");
        assertTrue(examples.get(0).getExist());
        assertFalse(examples.get(1).getName().isEmpty());
        assertEquals(examples.get(1).getName(), "andrew");
        assertFalse(examples.get(1).getExist());
        assertNotNull(examples);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    @DirtiesContext
    public void testUnMarshallMessageWithBooleanMissingFields() throws Exception {
       
        // We suppress the firstName field of the first record
        expected = "andrew,true\r\n" + "joseph,false\r\n" + "nicholas,\r\n";

        template.sendBody(expected);

        List<BooleanExample> examples = (List<BooleanExample>)result.getExchanges().get(0).getIn().getBody();
        
        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        
        assertFalse(examples.get(0).getName().isEmpty());
        assertEquals(examples.get(0).getName(), "andrew");
        assertTrue(examples.get(0).getExist());
        assertFalse(examples.get(1).getName().isEmpty());
        assertEquals(examples.get(1).getName(), "joseph");
        assertFalse(examples.get(1).getExist());
        assertFalse(examples.get(2).getName().isEmpty());
        assertEquals(examples.get(2).getName(), "nicholas");
        assertTrue(examples.get(2).getExist());
        assertNotNull(examples);
    }
    
    public static class ContextConfig extends RouteBuilder {
        BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat(
            org.apache.camel.dataformat.bindy.model.simple.bool.BooleanExample.class);

        @Override
        public void configure() {
            // from("file://src/test/data?move=./target/done").unmarshal(camelDataFormat).to("mock:result");

            // default should errors go to mock:error
            errorHandler(deadLetterChannel(URI_MOCK_ERROR).redeliveryDelay(0));

            onException(Exception.class).maximumRedeliveries(0).handled(true);

            from(URI_DIRECT_START).unmarshal(camelDataFormat).to(URI_MOCK_RESULT);
        }

    }
}
