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
package org.apache.camel.util;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OgnlHelperTest {

    /**
     * Tests correct splitting in case the OGNL expression contains method parameters with brackets.
     */
    @Test
    public void splitOgnlWithRegexInMethod() {
        String ognl = "header.cookie.replaceFirst(\".*;?iwanttoknow=([^;]+);?.*\", \"$1\")";
        assertFalse(OgnlHelper.isInvalidValidOgnlExpression(ognl));
        assertTrue(OgnlHelper.isValidOgnlExpression(ognl));

        List<String> strings = OgnlHelper.splitOgnl(ognl);
        assertEquals(3, strings.size());
        assertEquals("header", strings.get(0));
        assertEquals(".cookie", strings.get(1));
        assertEquals(".replaceFirst(\".*;?iwanttoknow=([^;]+);?.*\", \"$1\")", strings.get(2));
    }

    @Test
    public void splitOgnlWithParenthesisInQuotes() {
        String ognl = "body.replace(\"((\", \"--\")";
        assertFalse(OgnlHelper.isInvalidValidOgnlExpression(ognl));
        assertTrue(OgnlHelper.isValidOgnlExpression(ognl));

        List<String> strings = OgnlHelper.splitOgnl(ognl);
        assertEquals(2, strings.size());
        assertEquals("body", strings.get(0));
        assertEquals(".replace(\"((\", \"--\")", strings.get(1));
    }

    @Test
    public void splitOgnlWithParenthesisInQuotesTwo() {
        String ognl = "body.replace(\"((\", \"--\").replace(\"((((\", \"----\")";
        assertFalse(OgnlHelper.isInvalidValidOgnlExpression(ognl));
        assertTrue(OgnlHelper.isValidOgnlExpression(ognl));

        List<String> strings = OgnlHelper.splitOgnl(ognl);
        assertEquals(3, strings.size());
        assertEquals("body", strings.get(0));
        assertEquals(".replace(\"((\", \"--\")", strings.get(1));
        assertEquals(".replace(\"((((\", \"----\")", strings.get(2));
    }

}
