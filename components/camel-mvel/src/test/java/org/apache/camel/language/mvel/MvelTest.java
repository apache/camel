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
package org.apache.camel.language.mvel;

import org.apache.camel.test.junit5.LanguageTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MvelTest extends LanguageTestSupport {

    @Test
    public void testMvelExpressions() {
        assertExpression("exchange", exchange);
        assertExpression("exchange.getIn().body", "<hello id='m123'>world!</hello>");
        assertExpression("getRequest().body", "<hello id='m123'>world!</hello>");
        assertExpression("request.body", "<hello id='m123'>world!</hello>");
        assertExpression("getRequest().headers['foo']", "abc");
        assertExpression("getRequest().headers.foo", "abc");
        assertExpression("request.headers.foo", "abc");
    }

    @Test
    public void testGetOutFalseKeepsNullOutMessage() {
        assertExpression("exchange.hasOut()", false);
        assertFalse(exchange.hasOut());
    }

    @Test
    public void testResponseCreatesOutMessage() {
        assertExpression("response.body", null);
        assertTrue(exchange.hasOut());
    }

    @Override
    protected String getLanguageName() {
        return "mvel";
    }
}
