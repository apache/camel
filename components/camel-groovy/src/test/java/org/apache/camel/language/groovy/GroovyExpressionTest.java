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
package org.apache.camel.language.groovy;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class GroovyExpressionTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(GroovyExpressionTest.class);

    protected Exchange exchange;

    @Test
    public void testExpressionReturnsTheCorrectValue() throws Exception {
        assertExpression(GroovyLanguage.groovy("exchange.in.headers['foo.bar']"), exchange, "cheese");
        assertExpression(GroovyLanguage.groovy("exchange.in.headers.name"), exchange, "James");
        assertExpression(GroovyLanguage.groovy("exchange.in.headers['doesNotExist']"), exchange, null);
    }

    @Test
    public void testPredicateEvaluation() throws Exception {
        assertPredicate(GroovyLanguage.groovy("exchange.in.headers.name == 'James'"), exchange, true);
        assertPredicate(GroovyLanguage.groovy("exchange.in.headers.name == 'Hiram'"), exchange, false);

        assertPredicate(GroovyLanguage.groovy("request.headers.name == 'James'"), exchange, true);
    }

    @Test
    public void testProcessorMutatesTheExchange() throws Exception {
        GroovyLanguage.groovy("request.headers.myNewHeader = 'ABC'").evaluate(exchange);

        assertInMessageHeader(exchange, "myNewHeader", "ABC");
    }

    @Test
    public void testInvalidExpressionFailsWithMeaningfulException() throws Exception {
        try {
            GroovyLanguage.groovy("exchange.doesNotExist").evaluate(exchange);
            fail("This test case should have thrown an exception!");
        } catch (Exception e) {
            LOG.debug("Caught expected exception: " + e, e);
            String message = e.getMessage();
            assertTrue("The message should include 'doesNotExist' but was: " + message, message.contains("doesNotExist"));
        }
    }

    @Override
    @Before
    public void setUp() throws Exception {
        exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader("foo.bar", "cheese");
        exchange.getIn().setHeader("name", "James");
    }
}
