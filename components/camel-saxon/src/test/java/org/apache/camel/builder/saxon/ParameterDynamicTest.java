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
package org.apache.camel.builder.saxon;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.DynamicQueryContext;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.ObjectValue;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.xquery.XQueryBuilder.xquery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ParameterDynamicTest {

    private static final String TEST_QUERY = new StringBuilder()
        .append("xquery version \"3.0\" encoding \"UTF-8\";\n")
        .append("declare variable $extParam as xs:boolean external := false();\n")
        .append("if($extParam) then(true()) else (false())")
        .toString();

    private Configuration conf = new Configuration();
    private XQueryExpression query;
    private DynamicQueryContext context;

    @Before
    public void setup() throws Exception {
        conf.setCompileWithTracing(true);
        query = conf.newStaticQueryContext().compileQuery(TEST_QUERY);
        context = new DynamicQueryContext(conf);
    }

    /**
     * This is what Camel XQueryBuilder executes, which leads to a parameter binding type error.
     */
    @Test
    public void testObjectParameter() throws Exception {
        context.setParameter(StructuredQName.fromClarkName("extParam"), new ObjectValue<>(true));
        try {
            Item result = query.iterator(context).next();
            fail("Should have thrown an exception");
            assertTrue(result instanceof BooleanValue);
            assertEquals(true, ((BooleanValue) result).getBooleanValue());
        } catch (Exception e) {
            // expected
        }
    }

    /**
     * This is what Camel XQueryBuilder should execute to allow Saxon to bind the parameter type properly.
     */
    @Test
    public void testBooleanParameter() throws Exception {
        context.setParameter(StructuredQName.fromClarkName("extParam"), BooleanValue.TRUE);
        Item result = query.iterator(context).next();
        assertTrue(result instanceof BooleanValue);
        assertEquals(true, ((BooleanValue) result).getBooleanValue());
    }

    @Test
    public void testXQueryBuilder() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setBody("<foo><bar>abc_def_ghi</bar></foo>");
        exchange.setProperty("extParam", true);

        Object result = xquery(TEST_QUERY).asString().evaluate(exchange, boolean.class);
        assertEquals(true, result);

        exchange.setProperty("extParam", false);
        result = xquery(TEST_QUERY).asString().evaluate(exchange, boolean.class);
        assertEquals(false, result);
    }
}
