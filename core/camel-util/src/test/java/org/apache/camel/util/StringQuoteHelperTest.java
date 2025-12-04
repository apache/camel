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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StringQuoteHelperTest {

    @Test
    public void testSplitBeanParametersTrim() throws Exception {
        String[] arr = StringQuoteHelper.splitSafeQuote("String.class ${body}, String.class Mars", ',', true, true);
        Assertions.assertEquals(2, arr.length);
        Assertions.assertEquals("String.class ${body}", arr[0]);
        Assertions.assertEquals("String.class Mars", arr[1]);

        arr = StringQuoteHelper.splitSafeQuote("  String.class ${body}  , String.class Mars   ", ',', true, true);
        Assertions.assertEquals(2, arr.length);
        Assertions.assertEquals("String.class ${body}", arr[0]);
        Assertions.assertEquals("String.class Mars", arr[1]);
    }

    @Test
    public void testSplitBeanParametersNoTrim() throws Exception {
        String[] arr = StringQuoteHelper.splitSafeQuote("String.class ${body}, String.class Mars", ',', false, true);
        Assertions.assertEquals(2, arr.length);
        Assertions.assertEquals("String.class ${body}", arr[0]);
        Assertions.assertEquals(" String.class Mars", arr[1]);

        arr = StringQuoteHelper.splitSafeQuote("  String.class ${body}  , String.class Mars   ", ',', false, true);
        Assertions.assertEquals(2, arr.length);
        Assertions.assertEquals("  String.class ${body}  ", arr[0]);
        Assertions.assertEquals(" String.class Mars   ", arr[1]);
    }
}
