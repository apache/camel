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
package org.apache.camel.language.groovy;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.assertExpression;
import static org.apache.camel.test.junit5.TestSupport.assertInMessageHeader;
import static org.apache.camel.test.junit5.TestSupport.assertPredicate;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class GroovyExpressionTest {
    private static final Logger LOG = LoggerFactory.getLogger(GroovyExpressionTest.class);

    protected Exchange exchange;

    @Test
    public void testExpressionReturnsTheCorrectValue() {
        assertExpression(GroovyLanguage.groovy("exchange.in.headers['foo.bar']"), exchange, "cheese");
        assertExpression(GroovyLanguage.groovy("exchange.in.headers.name"), exchange, "James");
        assertExpression(GroovyLanguage.groovy("exchange.in.headers['doesNotExist']"), exchange, null);
    }

    @Test
    public void testPredicateEvaluation() {
        assertPredicate(GroovyLanguage.groovy("exchange.in.headers.name == 'James'"), exchange, true);
        assertPredicate(GroovyLanguage.groovy("exchange.in.headers.name == 'Hiram'"), exchange, false);

        assertPredicate(GroovyLanguage.groovy("request.headers.name == 'James'"), exchange, true);
    }

    @Test
    public void testProcessorMutatesTheExchange() {
        GroovyLanguage.groovy("request.headers.myNewHeader = 'ABC'").evaluate(exchange);

        assertInMessageHeader(exchange, "myNewHeader", "ABC");
    }

    @Test
    public void testInvalidExpressionFailsWithMeaningfulException() {
        try {
            GroovyLanguage.groovy("exchange.doesNotExist").evaluate(exchange);
            fail("This test case should have thrown an exception!");
        } catch (Exception e) {
            LOG.debug("Caught expected exception: {}", e.getMessage(), e);
            String message = e.getMessage();
            assertTrue(message.contains("doesNotExist"), "The message should include 'doesNotExist' but was: " + message);
        }
    }

    @BeforeEach
    public void setUp() {
        exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader("foo.bar", "cheese");
        exchange.getIn().setHeader("name", "James");
    }
}
