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
package org.apache.camel.jsonpath;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A path that gives a list of one value that does not convert to the result type gives null, the same as a single value
 * that does not convert.
 */
public class JsonPathResultTypeNotConvertedTest extends CamelTestSupport {

    private static final String BODY = "[{\"sku\":\"X\",\"qty\":42},{\"sku\":\"Y\",\"qty\":0}]";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private Object evaluate(String path, Class<?> resultType) {
        JsonPathExpression exp = new JsonPathExpression(path);
        exp.setResultType(resultType);
        exp.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(BODY);
        return exp.evaluate(exchange);
    }

    @Test
    public void testListElementNotConverted() {
        assertNull(evaluate("$[?(@.sku == 'X')].qty", Map.class));
    }

    @Test
    public void testSingleValueNotConverted() {
        assertNull(evaluate("$[0].qty", Map.class));
    }

    @Test
    public void testListElementConverted() {
        assertEquals("42", evaluate("$[?(@.sku == 'X')].qty", String.class));
    }
}
