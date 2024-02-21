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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 *
 */
public class StingQuoteHelperTest {

    @Test
    public void testSplitSafeQuote() throws Exception {
        assertNull(StringQuoteHelper.splitSafeQuote(null, ','));

        String[] out = StringQuoteHelper.splitSafeQuote("", ',');
        assertEquals(1, out.length);
        assertEquals("", out[0]);

        out = StringQuoteHelper.splitSafeQuote("   ", ',');
        assertEquals(1, out.length);
        assertEquals("", out[0]);

        out = StringQuoteHelper.splitSafeQuote("   ", ',', false);
        assertEquals(1, out.length);
        assertEquals("   ", out[0]);

        out = StringQuoteHelper.splitSafeQuote("Camel", ',');
        assertEquals(1, out.length);
        assertEquals("Camel", out[0]);

        out = StringQuoteHelper.splitSafeQuote("Hello Camel", ',');
        assertEquals(1, out.length);
        assertEquals("Hello Camel", out[0]);

        out = StringQuoteHelper.splitSafeQuote("Hello,Camel", ',');
        assertEquals(2, out.length);
        assertEquals("Hello", out[0]);
        assertEquals("Camel", out[1]);

        out = StringQuoteHelper.splitSafeQuote("Hello,Camel,Bye,World", ',');
        assertEquals(4, out.length);
        assertEquals("Hello", out[0]);
        assertEquals("Camel", out[1]);
        assertEquals("Bye", out[2]);
        assertEquals("World", out[3]);

        out = StringQuoteHelper.splitSafeQuote("'Hello,Camel','Bye,World'", ',');
        assertEquals(2, out.length);
        assertEquals("Hello,Camel", out[0]);
        assertEquals("Bye,World", out[1]);

        out = StringQuoteHelper.splitSafeQuote("'Hello,Camel',\"Bye,World\"", ',');
        assertEquals(2, out.length);
        assertEquals("Hello,Camel", out[0]);
        assertEquals("Bye,World", out[1]);

        out = StringQuoteHelper.splitSafeQuote("\"Hello,Camel\",'Bye,World'", ',');
        assertEquals(2, out.length);
        assertEquals("Hello,Camel", out[0]);
        assertEquals("Bye,World", out[1]);

        out = StringQuoteHelper.splitSafeQuote("\"Hello,Camel\",\"Bye,World\"", ',');
        assertEquals(2, out.length);
        assertEquals("Hello,Camel", out[0]);
        assertEquals("Bye,World", out[1]);

        out = StringQuoteHelper.splitSafeQuote("'Hello Camel', 'Bye World'", ',');
        assertEquals(2, out.length);
        assertEquals("Hello Camel", out[0]);
        assertEquals("Bye World", out[1]);

        out = StringQuoteHelper.splitSafeQuote("'Hello Camel', ' Bye World'", ',', false);
        assertEquals(2, out.length);
        assertEquals("Hello Camel", out[0]);
        assertEquals(" Bye World", out[1]);

        out = StringQuoteHelper.splitSafeQuote("'http:', ' '", ',', false);
        assertEquals(2, out.length);
        assertEquals("http:", out[0]);
        assertEquals(" ", out[1]);

        out = StringQuoteHelper.splitSafeQuote("'http:', ''", ',', false);
        assertEquals(2, out.length);
        assertEquals("http:", out[0]);
        assertEquals("", out[1]);

        out = StringQuoteHelper.splitSafeQuote("'Hello Camel', 5, true", ',', false);
        assertEquals(3, out.length);
        assertEquals("Hello Camel", out[0]);
        assertEquals("5", out[1]);
        assertEquals("true", out[2]);

        out = StringQuoteHelper.splitSafeQuote("'Hello Camel',5,true", ',', false);
        assertEquals(3, out.length);
        assertEquals("Hello Camel", out[0]);
        assertEquals("5", out[1]);
        assertEquals("true", out[2]);

        out = StringQuoteHelper.splitSafeQuote("   'Hello Camel',  5   ,  true   ", ',', false);
        assertEquals(3, out.length);
        assertEquals("Hello Camel", out[0]);
        assertEquals("5", out[1]);
        assertEquals("true", out[2]);

        out = StringQuoteHelper.splitSafeQuote("*, '', 'arg3'", ',', false);
        assertEquals(3, out.length);
        assertEquals("*", out[0]);
        assertEquals("", out[1]);
        assertEquals("arg3", out[2]);

        out = StringQuoteHelper.splitSafeQuote("'Hello'", ',', true);
        assertEquals(1, out.length);
        assertEquals("Hello", out[0]);

        out = StringQuoteHelper.splitSafeQuote("' Hello '", ',', true);
        assertEquals(1, out.length);
        assertEquals("Hello", out[0]);

        out = StringQuoteHelper.splitSafeQuote("' Hello '", ',', false);
        assertEquals(1, out.length);
        assertEquals(" Hello ", out[0]);

        out = StringQuoteHelper.splitSafeQuote("'Hello', 'World'", ',', true);
        assertEquals(2, out.length);
        assertEquals("Hello", out[0]);
        assertEquals("World", out[1]);

        out = StringQuoteHelper.splitSafeQuote("\"Hello\"", ',', true);
        assertEquals(1, out.length);
        assertEquals("Hello", out[0]);

        out = StringQuoteHelper.splitSafeQuote("\" Hello \"", ',', true);
        assertEquals(1, out.length);
        assertEquals("Hello", out[0]);

        out = StringQuoteHelper.splitSafeQuote("\" Hello \"", ',', false);
        assertEquals(1, out.length);
        assertEquals(" Hello ", out[0]);

        out = StringQuoteHelper.splitSafeQuote("\"Hello\", \"World\"", ',', true);
        assertEquals(2, out.length);
        assertEquals("Hello", out[0]);
        assertEquals("World", out[1]);
    }

    @Test
    public void testLastIsQuote() throws Exception {
        String[] out = StringQuoteHelper.splitSafeQuote(" ${body}, 5, 'Hello World'", ',', true);
        assertEquals(3, out.length);
        assertEquals("${body}", out[0]);
        assertEquals("5", out[1]);
        assertEquals("Hello World", out[2]);

        out = StringQuoteHelper.splitSafeQuote(" ${body}, 5, \"Hello World\"", ',', true);
        assertEquals(3, out.length);
        assertEquals("${body}", out[0]);
        assertEquals("5", out[1]);
        assertEquals("Hello World", out[2]);
    }

    @Test
    public void testSingleInDoubleQuote() throws Exception {
        String[] out = StringQuoteHelper.splitSafeQuote("\"Hello O'Connor\", 5, 'foo bar'", ',', true);
        assertEquals(3, out.length);
        assertEquals("Hello O'Connor", out[0]);
        assertEquals("5", out[1]);
        assertEquals("foo bar", out[2]);

        out = StringQuoteHelper.splitSafeQuote("\"Hello O'Connor O'Bannon\", 5, 'foo bar'", ',', true);
        assertEquals(3, out.length);
        assertEquals("Hello O'Connor O'Bannon", out[0]);
        assertEquals("5", out[1]);
        assertEquals("foo bar", out[2]);
    }

    @Test
    public void testDoubleInSingleQuote() throws Exception {
        String[] out = StringQuoteHelper.splitSafeQuote("'Hello O\"Connor', 5, 'foo bar'", ',', true);
        assertEquals(3, out.length);
        assertEquals("Hello O\"Connor", out[0]);
        assertEquals("5", out[1]);
        assertEquals("foo bar", out[2]);

        out = StringQuoteHelper.splitSafeQuote("'Hello O\"Connor O\"Bannon', 5, 'foo bar'", ',', true);
        assertEquals(3, out.length);
        assertEquals("Hello O\"Connor O\"Bannon", out[0]);
        assertEquals("5", out[1]);
        assertEquals("foo bar", out[2]);
    }

    @Test
    public void testSpaceSeparator() throws Exception {
        String[] out = StringQuoteHelper
                .splitSafeQuote("dependency=mvn:org.my:application:1.0 dependency=mvn:com.foo:myapp:2.1", ' ');
        assertEquals(2, out.length);
        assertEquals("dependency=mvn:org.my:application:1.0", out[0]);
        assertEquals("dependency=mvn:com.foo:myapp:2.1", out[1]);
    }

    @Test
    public void testSpaceSeparatorQuote() throws Exception {
        String[] out = StringQuoteHelper.splitSafeQuote(
                "dependency=mvn:org.my:application:1.0 property=hi='Hello World' dependency=mvn:com.foo:myapp:2.1", ' ');
        assertEquals(3, out.length);
        assertEquals("dependency=mvn:org.my:application:1.0", out[0]);
        assertEquals("property=hi=Hello World", out[1]);
        assertEquals("dependency=mvn:com.foo:myapp:2.1", out[2]);
    }

    @Test
    public void testKeepQuotes() throws Exception {
        String[] out = StringQuoteHelper.splitSafeQuote("'body'", ',', false, true);
        assertEquals(1, out.length);
        assertEquals("'body'", out[0]);

        out = StringQuoteHelper.splitSafeQuote("'body', 123", ',', false, true);
        assertEquals(2, out.length);
        assertEquals("'body'", out[0]);
        assertEquals("123", out[1]);

        out = StringQuoteHelper.splitSafeQuote("'body', \"world\"", ',', false, true);
        assertEquals(2, out.length);
        assertEquals("'body'", out[0]);
        assertEquals("\"world\"", out[1]);

        out = StringQuoteHelper.splitSafeQuote("'body', \"world\", 123", ',', false, true);
        assertEquals(3, out.length);
        assertEquals("'body'", out[0]);
        assertEquals("\"world\"", out[1]);
        assertEquals("123", out[2]);
    }

}
