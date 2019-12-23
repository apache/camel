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

import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class OgnlHelperTest extends Assert {

    @Test
    public void testSplitOgnlSimple() throws Exception {
        List<String> methods = OgnlHelper.splitOgnl(null);
        assertEquals(0, methods.size());
        methods = OgnlHelper.splitOgnl("");
        assertEquals(0, methods.size());
        methods = OgnlHelper.splitOgnl(" ");
        assertEquals(0, methods.size());

        methods = OgnlHelper.splitOgnl("foo");
        assertEquals(1, methods.size());
        assertEquals("foo", methods.get(0));

        methods = OgnlHelper.splitOgnl("foo.bar");
        assertEquals(2, methods.size());
        assertEquals("foo", methods.get(0));
        assertEquals(".bar", methods.get(1));

        methods = OgnlHelper.splitOgnl("foo.bar.baz");
        assertEquals(3, methods.size());
        assertEquals("foo", methods.get(0));
        assertEquals(".bar", methods.get(1));
        assertEquals(".baz", methods.get(2));
    }

    @Test
    public void testSplitOgnlSquare() throws Exception {
        List<String> methods = OgnlHelper.splitOgnl("foo");
        assertEquals(1, methods.size());
        assertEquals("foo", methods.get(0));

        methods = OgnlHelper.splitOgnl("foo[0].bar");
        assertEquals(2, methods.size());
        assertEquals("foo[0]", methods.get(0));
        assertEquals(".bar", methods.get(1));

        methods = OgnlHelper.splitOgnl("foo[0]?.bar");
        assertEquals(2, methods.size());
        assertEquals("foo[0]", methods.get(0));
        assertEquals("?.bar", methods.get(1));

        methods = OgnlHelper.splitOgnl("foo['key'].bar");
        assertEquals(2, methods.size());
        assertEquals("foo['key']", methods.get(0));
        assertEquals(".bar", methods.get(1));

        methods = OgnlHelper.splitOgnl("foo['key']?.bar");
        assertEquals(2, methods.size());
        assertEquals("foo['key']", methods.get(0));
        assertEquals("?.bar", methods.get(1));

        methods = OgnlHelper.splitOgnl("foo['key'].bar[0]");
        assertEquals(2, methods.size());
        assertEquals("foo['key']", methods.get(0));
        assertEquals(".bar[0]", methods.get(1));

        methods = OgnlHelper.splitOgnl("foo['key']?.bar[0]");
        assertEquals(2, methods.size());
        assertEquals("foo['key']", methods.get(0));
        assertEquals("?.bar[0]", methods.get(1));
    }

    @Test
    public void testSplitOgnlParenthesis() throws Exception {
        List<String> methods = OgnlHelper.splitOgnl("foo()");
        assertEquals(1, methods.size());
        assertEquals("foo()", methods.get(0));

        methods = OgnlHelper.splitOgnl("foo(${body})");
        assertEquals(1, methods.size());
        assertEquals("foo(${body})", methods.get(0));

        methods = OgnlHelper.splitOgnl("foo(${body}, ${header.foo})");
        assertEquals(1, methods.size());
        assertEquals("foo(${body}, ${header.foo})", methods.get(0));

        methods = OgnlHelper.splitOgnl("foo(${body}, ${header.foo}).bar");
        assertEquals(2, methods.size());
        assertEquals("foo(${body}, ${header.foo})", methods.get(0));
        assertEquals(".bar", methods.get(1));

        methods = OgnlHelper.splitOgnl("foo(${body}, ${header.foo}).bar(true, ${header.bar})");
        assertEquals(2, methods.size());
        assertEquals("foo(${body}, ${header.foo})", methods.get(0));
        assertEquals(".bar(true, ${header.bar})", methods.get(1));

        methods = OgnlHelper.splitOgnl("foo(${body}, ${header.foo}).bar(true, ${header.bar}).baz['key']");
        assertEquals(3, methods.size());
        assertEquals("foo(${body}, ${header.foo})", methods.get(0));
        assertEquals(".bar(true, ${header.bar})", methods.get(1));
        assertEquals(".baz['key']", methods.get(2));
    }

    @Test
    public void testSplitOgnlParenthesisAndBracket() throws Exception {
        List<String> methods = OgnlHelper.splitOgnl("foo(${body['key']})");
        assertEquals(1, methods.size());
        assertEquals("foo(${body['key']})", methods.get(0));

        methods = OgnlHelper.splitOgnl("foo(${body}, ${header.foo?['key']})");
        assertEquals(1, methods.size());
        assertEquals("foo(${body}, ${header.foo?['key']})", methods.get(0));

        methods = OgnlHelper.splitOgnl("foo(${body}, ${header.foo}).bar(true, ${header.bar[0]?.code})");
        assertEquals(2, methods.size());
        assertEquals("foo(${body}, ${header.foo})", methods.get(0));
        assertEquals(".bar(true, ${header.bar[0]?.code})", methods.get(1));
    }

}
