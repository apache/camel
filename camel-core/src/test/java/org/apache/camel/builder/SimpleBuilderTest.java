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
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.TestSupport;
import org.apache.camel.TypeConversionException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version
 */
public class SimpleBuilderTest extends TestSupport {

    protected Exchange exchange = new DefaultExchange(new DefaultCamelContext());

    public void testPredicate() throws Exception {
        exchange.getIn().setBody("foo");

        assertTrue(SimpleBuilder.simple("${body} == 'foo'").matches(exchange));
        assertFalse(SimpleBuilder.simple("${body} == 'bar'").matches(exchange));
    }

    public void testExpression() throws Exception {
        exchange.getIn().setBody("foo");

        assertEquals("foo", SimpleBuilder.simple("${body}").evaluate(exchange, String.class));
        assertNull(SimpleBuilder.simple("${header.cheese}").evaluate(exchange, String.class));
    }

    public void testFormatExpression() throws Exception {
        exchange.getIn().setHeader("head", "foo");

        assertEquals("foo", SimpleBuilder.simpleF("${header.%s}", "head").evaluate(exchange, String.class));
        assertNull(SimpleBuilder.simple("${header.cheese}").evaluate(exchange, String.class));
    }

    public void testFormatExpressionWithResultType() throws Exception {
        exchange.getIn().setHeader("head", "200");

        assertEquals(200, SimpleBuilder.simpleF("${header.%s}", Integer.class, "head").evaluate(exchange, Object.class));
    }

    public void testResultType() throws Exception {
        exchange.getIn().setBody("foo");
        exchange.getIn().setHeader("cool", true);

        assertEquals("foo", SimpleBuilder.simple("${body}", String.class).evaluate(exchange, Object.class));
        try {
            // error during conversion
            SimpleBuilder.simple("${body}", int.class).evaluate(exchange, Object.class);
            fail("Should have thrown exception");
        } catch (TypeConversionException e) {
            assertIsInstanceOf(NumberFormatException.class, e.getCause().getCause());
        }

        assertEquals(true, SimpleBuilder.simple("${header.cool}", boolean.class).evaluate(exchange, Object.class));
        assertEquals("true", SimpleBuilder.simple("${header.cool}", String.class).evaluate(exchange, Object.class));
        // not possible
        assertEquals(null, SimpleBuilder.simple("${header.cool}", int.class).evaluate(exchange, Object.class));

        assertEquals(true, SimpleBuilder.simple("${header.cool}").resultType(Boolean.class).evaluate(exchange, Object.class));
        assertEquals("true", SimpleBuilder.simple("${header.cool}").resultType(String.class).evaluate(exchange, Object.class));
        // not possible
        assertEquals(null, SimpleBuilder.simple("${header.cool}").resultType(int.class).evaluate(exchange, Object.class));

        // should be convertable to integers
        assertEquals(11, SimpleBuilder.simple("11", int.class).evaluate(exchange, Object.class));
    }
}
