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

public class TimePatternConverterTest extends ContextTestSupport {

    public void testMillisecondsTimePattern() throws Exception {
        String source = new String("444");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(444, milliseconds);
    }
    
    public void testMilliseconds2TimePattern() throws Exception {
        String source = new String("-72");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(-72, milliseconds);
    }
    
    public void testSTimePattern() throws Exception {
        String source = new String("35s");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(35000, milliseconds);
    }
    
    public void testConsiderLegalSTimePattern() throws Exception {
        String source = new String("89s");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(89000, milliseconds);
    }

    public void testMTimePattern() throws Exception {
        String source = new String("28m");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(1680000, milliseconds);
    }

    public void testConsiderLegalMTimePattern() throws Exception {
        String source = new String("89m");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(5340000, milliseconds);
    }

    public void testHMSTimePattern() throws Exception {
        String source = new String("1h3m5s");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(3785000, milliseconds);
    }

    public void testHMSTimePattern2() throws Exception {
        String source = new String("1hours30minutes1second");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(5401000, milliseconds);
    }
    
    public void testHMSTimePattern3() throws Exception {
        String source = new String("1HOUR3m5s");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(3785000, milliseconds);
    }

    public void testMSTimePattern() throws Exception {
        String source = new String("30m55s");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(1855000, milliseconds);        
    }
    
    public void testHMTimePattern() throws Exception {
        String source = new String("1h30m");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(5400000, milliseconds);
    }

    public void testSTimePattern2() throws Exception {
        String source = new String("15sec");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(15000, milliseconds);
    }

    public void testMTimePattern2() throws Exception {
        String source = new String("5min");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(300000, milliseconds);
    }
    
    public void testMTimePattern3() throws Exception {
        String source = new String("5MIN");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(300000, milliseconds);
    }
    
    public void testMSTimePattern2() throws Exception {
        String source = new String("4min2sec");
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(242000, milliseconds);
    }    
    
    //Negative Tests    
    public void testIllegalHMSTimePattern() {
        String source = new String("36h88m5s");
        try {
            TimePatternConverter.toMilliSeconds(source);
            fail("Should throw IllegalArgumentException");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e);
            assertEquals("Minutes should contain a valid value between 0 and 59: 36h88m5s", e.getMessage());
        }
    }

    public void testIllegalMSTimePattern() {
        String source = new String("55m75s");
        try {
            TimePatternConverter.toMilliSeconds(source);
            fail("Should throw IllegalArgumentException");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e);
            assertEquals("Seconds should contain a valid value between 0 and 59: 55m75s", e.getMessage());
        }
    }
    
    public void testIllegalHMTimePattern() throws Exception {
        String source = new String("1h89s");
        try {
            TimePatternConverter.toMilliSeconds(source);
            fail("Should throw IllegalArgumentException");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e);
            assertEquals("Seconds should contain a valid value between 0 and 59: 1h89s", e.getMessage());
        }
    }    
    
}
