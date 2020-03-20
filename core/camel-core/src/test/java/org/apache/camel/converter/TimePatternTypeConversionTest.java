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
package org.apache.camel.converter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.util.TimeUtils;
import org.junit.Test;

public class TimePatternTypeConversionTest extends ContextTestSupport {

    @Test
    public void testForNoSideEffects() throws Exception {
        long milliseconds = TimeUtils.toMilliSeconds("444");
        assertEquals(Long.valueOf("444").longValue(), milliseconds);
    }

    @Test
    public void testForNoSideEffects2() throws Exception {
        long milliseconds = TimeUtils.toMilliSeconds("-72");
        assertEquals(Long.valueOf("-72").longValue(), milliseconds);
    }

    @Test
    public void testHMSTimePattern() throws Exception {
        long milliseconds = TimeUtils.toMilliSeconds("1hours30m1s");
        assertEquals(5401000, milliseconds);
    }

    @Test
    public void testMTimePattern() throws Exception {
        long milliseconds = TimeUtils.toMilliSeconds("30m55s");
        assertEquals(1855000, milliseconds);
    }
}
