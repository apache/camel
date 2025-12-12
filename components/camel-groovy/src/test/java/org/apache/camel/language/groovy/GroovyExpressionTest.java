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

import static org.apache.camel.test.junit6.TestSupport.assertExpression;
import static org.apache.camel.test.junit6.TestSupport.assertInMessageHeader;
import static org.apache.camel.test.junit6.TestSupport.assertPredicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GroovyExpressionTest {
    private static final Logger LOG = LoggerFactory.getLogger(GroovyExpressionTest.class);

    protected Exchange exchange;

    @Test
    public void testExpressionReturnsTheCorrectValue() {
        assertExpression(GroovyLanguage.groovy("exchange.in.headers['foo.bar']"), exchange, "cheese");
        assertExpression(GroovyLanguage.groovy("exchange.in.headers.name"), exchange, "James");
        assertExpression(GroovyLanguage.groovy("exchange.in.headers['doesNotExist']"), exchange, null);

        assertExpression(GroovyLanguage.groovy("header['foo.bar']"), exchange, "cheese");
        assertExpression(GroovyLanguage.groovy("header.name"), exchange, "James");
        assertExpression(GroovyLanguage.groovy("header['doesNotExist']"), exchange, null);

        assertExpression(GroovyLanguage.groovy("variable['cheese']"), exchange, "gauda");
        assertExpression(GroovyLanguage.groovy("variable.cheese"), exchange, "gauda");
        assertExpression(GroovyLanguage.groovy("variable['doesNotExist']"), exchange, null);
        assertExpression(GroovyLanguage.groovy("variables['cheese']"), exchange, "gauda");
        assertExpression(GroovyLanguage.groovy("variables.cheese"), exchange, "gauda");
        assertExpression(GroovyLanguage.groovy("variables['doesNotExist']"), exchange, null);
    }

    @Test
    public void testPredicateEvaluation() {
        assertPredicate(GroovyLanguage.groovy("exchange.in.headers.name == 'James'"), exchange, true);
        assertPredicate(GroovyLanguage.groovy("header.name == 'James'"), exchange, true);
        assertPredicate(GroovyLanguage.groovy("exchange.in.headers.name == 'Hiram'"), exchange, false);
        assertPredicate(GroovyLanguage.groovy("header.name == 'Hiram'"), exchange, false);

        assertPredicate(GroovyLanguage.groovy("request.headers.name == 'James'"), exchange, true);
        assertPredicate(GroovyLanguage.groovy("header.name == 'James'"), exchange, true);

        assertPredicate(GroovyLanguage.groovy("variable.cheese == 'gauda'"), exchange, true);
        assertPredicate(GroovyLanguage.groovy("variables.cheese == 'gauda'"), exchange, true);
        assertPredicate(GroovyLanguage.groovy("variables['cheese'] == 'gauda'"), exchange, true);
    }

    @Test
    public void testVariableHeaders() {
        exchange.removeVariable("cheese");
        exchange.setVariable("header:myKey.foo", "abc");
        exchange.setVariable("header:myKey.bar", 123);
        exchange.setVariable("myOtherKey", "Hello Again");

        assertEquals("Hello Again", GroovyLanguage.groovy("variables['myOtherKey']").evaluate(exchange));
        assertEquals("abc", GroovyLanguage.groovy("variables['header:myKey.foo']").evaluate(exchange));
        assertEquals(123, GroovyLanguage.groovy("variables['header:myKey.bar']").evaluate(exchange));
    }

    @Test
    public void testException() {
        Exception e = new IllegalArgumentException("Forced");

        exchange.setException(e);
        assertExpression(GroovyLanguage.groovy("exception"), exchange, e);

        exchange.setException(null);
        assertExpression(GroovyLanguage.groovy("exception"), exchange, null);

        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
        assertExpression(GroovyLanguage.groovy("exception"), exchange, e);
    }

    @Test
    public void testProcessorMutatesTheExchange() {
        GroovyLanguage.groovy("request.headers.myNewHeader = 'ABC'").evaluate(exchange);

        assertInMessageHeader(exchange, "myNewHeader", "ABC");
    }

    @Test
    public void testInvalidExpressionFailsWithMeaningfulException() {
        Exception e = assertThrows(Exception.class, () -> {
            GroovyLanguage.groovy("exchange.doesNotExist").evaluate(exchange);
        });
        LOG.debug("Caught expected exception: {}", e.getMessage(), e);
        String message = e.getMessage();
        assertTrue(message.contains("doesNotExist"), "The message should include 'doesNotExist' but was: " + message);
    }

    @BeforeEach
    public void setUp() {
        exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader("foo.bar", "cheese");
        exchange.getIn().setHeader("name", "James");
        exchange.setVariable("cheese", "gauda");
    }
}
