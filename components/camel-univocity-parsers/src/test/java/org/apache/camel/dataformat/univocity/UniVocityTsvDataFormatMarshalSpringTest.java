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

/**
 * This class tests the marshalling of {@link org.apache.camel.dataformat.univocity.UniVocityTsvDataFormat} using the
 * Spring DSL.
 */
public final class UniVocityTsvDataFormatMarshalSpringTest extends CamelSpringTestSupport {
    @EndpointInject("mock:result")
    MockEndpoint result;

    /**
     * Tests that we can marshal TSV with the default configuration.
     */
    @Test
    public void shouldMarshalWithDefaultConfiguration() throws Exception {
        template.sendBody("direct:default", Arrays.asList(
                asMap("A", "1", "B", "2", "C", "3"),
                asMap("A", "one", "B", "two", "C", "three")
        ));

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        String body = assertIsInstanceOf(String.class, result.getExchanges().get(0).getIn().getBody());
        assertEquals(join("1\t2\t3", "one\ttwo\tthree"), body);
    }

    /**
     * Tests that we can marshal a single line with TSV.
     */
    @Test
    public void shouldMarshalSingleLine() throws Exception {
        template.sendBody("direct:default", asMap("A", "1", "B", "2", "C", "3"));

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        String body = assertIsInstanceOf(String.class, result.getExchanges().get(0).getIn().getBody());
        assertEquals(join("1\t2\t3"), body);
    }

    /**
     * Tests that the marshalling adds new columns on the fly and keep its order
     */
    @Test
    public void shouldMarshalAndAddNewColumns() throws Exception {
        template.sendBody("direct:default", Arrays.asList(
                asMap("A", "1", "B", "2"),
                asMap("C", "three", "A", "one", "B", "two")
        ));

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        String body = assertIsInstanceOf(String.class, result.getExchanges().get(0).getIn().getBody());
        assertEquals(join("1\t2", "one\ttwo\tthree"), body);
    }

    /**
     * Tests that we can marshal TSV with specific headers
     */
    @Test
    public void shouldMarshalWithSpecificHeaders() throws Exception {
        template.sendBody("direct:header", Arrays.asList(
                asMap("A", "1", "B", "2", "C", "3"),
                asMap("A", "one", "B", "two", "C", "three")
        ));

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        String body = assertIsInstanceOf(String.class, result.getExchanges().get(0).getIn().getBody());
        assertEquals(join("1\t3", "one\tthree"), body);
    }

    /**
     * Tests that we can marshal TSV using and advanced configuration
     */
    @Test
    public void shouldMarshalUsingAdvancedConfiguration() throws Exception {
        template.sendBody("direct:advanced", Arrays.asList(
                asMap("A", null, "B", "", "C", "_"),
                asMap("A", "one", "B", "two", "C", "three")
        ));

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        String body = assertIsInstanceOf(String.class, result.getExchanges().get(0).getIn().getBody());
        assertEquals(join("N/A\tempty\t_", "one\ttwo\tthree"), body);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/univocity/UniVocityTsvDataFormatMarshalSpringTest.xml");
    }
}
