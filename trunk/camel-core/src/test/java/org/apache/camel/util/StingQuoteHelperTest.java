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
package org.apache.camel.util;

import junit.framework.TestCase;

/**
 *
 */
public class StingQuoteHelperTest extends TestCase {

    public void testSplitSafeQuote() throws Exception {
        assertEquals(null, StringQuoteHelper.splitSafeQuote(null, ','));

        String[] out = StringQuoteHelper.splitSafeQuote("", ',');
        assertEquals(0, out.length);

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

        out = StringQuoteHelper.splitSafeQuote("'Hello Camel', 'Bye World'", ',', false);
        assertEquals(2, out.length);
        assertEquals("Hello Camel", out[0]);
        assertEquals(" Bye World", out[1]);
    }

}
