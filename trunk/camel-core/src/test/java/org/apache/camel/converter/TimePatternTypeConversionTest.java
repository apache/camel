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
package org.apache.camel.converter;

import org.apache.camel.ContextTestSupport;

public class TimePatternTypeConversionTest extends ContextTestSupport {

    public void testForNoSideEffects() throws Exception {
        long milliseconds = context.getTypeConverter().convertTo(long.class, "444");
        assertEquals(Long.valueOf("444").longValue(), milliseconds);
    }
    
    public void testForNoSideEffects2() throws Exception {
        long milliseconds = context.getTypeConverter().convertTo(long.class, "-72");
        assertEquals(Long.valueOf("-72").longValue(), milliseconds);
    }
    
    public void testHMSTimePattern() throws Exception {
        long milliseconds = context.getTypeConverter().convertTo(long.class, "1hours30m1s");
        assertEquals(5401000, milliseconds);
    }
    
    public void testMTimePattern() throws Exception {
        long milliseconds = context.getTypeConverter().convertTo(long.class, "30m55s");
        assertEquals(1855000, milliseconds);
    }
}
