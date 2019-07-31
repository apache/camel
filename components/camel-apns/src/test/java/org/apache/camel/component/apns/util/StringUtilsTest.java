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
package org.apache.camel.component.apns.util;

import org.junit.Assert;
import org.junit.Test;

public class StringUtilsTest {

    @Test
    public void testIsEmpty() {
        Assert.assertFalse(StringUtils.isEmpty("test"));
        Assert.assertFalse(StringUtils.isEmpty("a"));
        Assert.assertTrue(StringUtils.isEmpty(""));
        Assert.assertTrue(StringUtils.isEmpty(null));
    }

    @Test
    public void testIsNotEmpty() {
        Assert.assertTrue(StringUtils.isNotEmpty("test"));
        Assert.assertTrue(StringUtils.isNotEmpty("a"));
        Assert.assertFalse(StringUtils.isNotEmpty(""));
        Assert.assertFalse(StringUtils.isNotEmpty(null));
    }

    @Test
    public void testTrim() {
        Assert.assertEquals("", StringUtils.trim(""));
        Assert.assertEquals("", StringUtils.trim(" "));

        Assert.assertEquals("test", StringUtils.trim("test"));
        Assert.assertEquals("test", StringUtils.trim("test "));
        Assert.assertEquals("test", StringUtils.trim(" test"));
        Assert.assertEquals("test", StringUtils.trim(" test "));

        Assert.assertEquals(null, StringUtils.trim(null));
    }

}
