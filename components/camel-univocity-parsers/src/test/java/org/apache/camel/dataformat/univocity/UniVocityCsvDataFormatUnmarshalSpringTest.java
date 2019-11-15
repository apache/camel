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
package org.apache.camel.dataformat.univocity;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.camel.dataformat.univocity.UniVocityTestHelper.asMap;
import static org.apache.camel.dataformat.univocity.UniVocityTestHelper.join;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This class tests the unmarshalling of {@link org.apache.camel.dataformat.univocity.UniVocityCsvDataFormat} using the
 * Spring DSL.
 */
public final class UniVocityCsvDataFormatUnmarshalSpringTest extends CamelSpringTestSupport {
    @EndpointInject("mock:result")
    MockEndpoint result;

    /**
     * Tests that we can unmarshal CSV with the default configuration.
     */
    @Test
    public void shouldUnmarshalWithDefaultConfiguration() throws Exception {
        template.sendBody("direct:default", join("A,B,C", "1,2,3", "one,two,three"));

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        List<?> body = assertIsInstanceOf(List.class, result.getExchanges().get(0).getIn().getBody());
        assertEquals(3, body.size());
        assertEquals(Arrays.asList("A", "B", "C"), body.get(0));
        assertEquals(Arrays.asList("1", "2", "3"), body.get(1));
        assertEquals(Arrays.asList("one", "two", "three"), body.get(2));
    }

    /**
     * Tests that we can unmarshal CSV and produce maps for each row
     */
    @Test
    public void shouldUnmarshalAsMap() throws Exception {
        template.sendBody("direct:map", join("A,B,C", "1,2,3", "one,two,three"));

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        List<?> body = assertIsInstanceOf(List.class, result.getExchanges().get(0).getIn().getBody());
        assertEquals(2, body.size());
        assertEquals(asMap("A", "1", "B", "2", "C", "3"), body.get(0));
        assertEquals(asMap("A", "one", "B", "two", "C", "three"), body.get(1));
    }

    /**
     * Tests that we can unmarshal CSV and produce maps for each row with the given header
     */
    @Test
    public void shouldUnmarshalAsMapWithHeaders() throws Exception {
        template.sendBody("direct:mapWithHeaders", join("1,2,3", "one,two,three"));

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        List<?> body = assertIsInstanceOf(List.class, result.getExchanges().get(0).getIn().getBody());
        assertEquals(2, body.size());
        assertEquals(asMap("A", "1", "B", "2", "C", "3"), body.get(0));
        assertEquals(asMap("A", "one", "B", "two", "C", "three"), body.get(1));
    }

    /**
     * Tests that we can unmarshal CSV and produce an Iterator that lazily reads the input
     */
    @Test
    public void shouldUnmarshalUsingIterator() throws Exception {
        template.sendBody("direct:lazy", join("A,B,C", "1,2,3", "one,two,three"));

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        Iterator<?> body = assertIsInstanceOf(Iterator.class, result.getExchanges().get(0).getIn().getBody());

        // Read first line
        assertTrue(body.hasNext());
        assertEquals(Arrays.asList("A", "B", "C"), body.next());

        // Try to remove the element
        try {
            body.remove();
            fail("Should have thrown a UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Success
        }

        // Read all the lines
        assertTrue(body.hasNext());
        assertEquals(Arrays.asList("1", "2", "3"), body.next());
        assertTrue(body.hasNext());
        assertEquals(Arrays.asList("one", "two", "three"), body.next());
        assertFalse(body.hasNext());

        // Try to read one more element
        try {
            body.next();
            fail("Should have thrown a NoSuchElementException");
        } catch (NoSuchElementException e) {
            // Success
        }
    }

    /**
     * Tests that we can unmarshal CSV that has lots of configuration options
     */
    @Test
    public void shouldUnmarshalUsingAdvancedConfiguration() throws Exception {
        template.sendBody("direct:advanced", join("!This is comment", "!This is comment too", "A;B", "", "  ;D  "));

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        List<?> body = assertIsInstanceOf(List.class, result.getExchanges().get(0).getIn().getBody());
        assertEquals(2, body.size());
        assertEquals(Arrays.asList("A", "B"), body.get(0));
        assertEquals(Arrays.asList("N/A", "D  "), body.get(1));
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/univocity/UniVocityCsvDataFormatUnmarshalSpringTest.xml");
    }
}
