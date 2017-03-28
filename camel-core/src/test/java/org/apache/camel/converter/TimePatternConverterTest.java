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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class TimePatternConverterTest extends ContextTestSupport {

    public void testMillisecondsTimePattern() throws Exception {
        String source = "444";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(444, milliseconds);
    }
    
    public void testMilliseconds2TimePattern() throws Exception {
        String source = "-72";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(-72, milliseconds);
    }
    
    public void testSTimePattern() throws Exception {
        String source = "35s";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(35000, milliseconds);
    }

    public void testSecTimePattern() throws Exception {
        String source = "35sec";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(35000, milliseconds);
    }

    public void testSecsTimePattern() throws Exception {
        String source = "35secs";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(35000, milliseconds);
    }

    public void testSecondTimePattern() throws Exception {
        String source = "35second";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(35000, milliseconds);
    }

    public void testSecondsTimePattern() throws Exception {
        String source = "35seconds";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(35000, milliseconds);
    }

    public void testConsiderLegalSTimePattern() throws Exception {
        String source = "89s";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(89000, milliseconds);
    }

    public void testMTimePattern() throws Exception {
        String source = "28m";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(1680000, milliseconds);
    }

    public void testMinTimePattern() throws Exception {
        String source = "28min";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(1680000, milliseconds);
    }

    public void testMinuteTimePattern() throws Exception {
        String source = "28MINUTE";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(1680000, milliseconds);
    }

    public void testMinutesTimePattern() throws Exception {
        String source = "28Minutes";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(1680000, milliseconds);
    }

    public void testConsiderLegalMTimePattern() throws Exception {
        String source = "89m";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(5340000, milliseconds);
    }

    public void testHTimePattern() throws Exception {
        String source = "28h";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(100800000, milliseconds);
    }

    public void testHourTimePattern() throws Exception {
        String source = "28Hour";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(100800000, milliseconds);
    }

    public void testHoursTimePattern() throws Exception {
        String source = "28HOURS";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(100800000, milliseconds);
    }

    public void testHMSTimePattern() throws Exception {
        String source = "1h3m5s";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(3785000, milliseconds);
    }

    public void testHMSTimePattern2() throws Exception {
        String source = "1hours30minutes1second";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(5401000, milliseconds);
    }
    
    public void testHMSTimePattern3() throws Exception {
        String source = "1HOUR3m5s";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(3785000, milliseconds);
    }

    public void testMSTimePattern() throws Exception {
        String source = "30m55s";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(1855000, milliseconds);        
    }
    
    public void testHMTimePattern() throws Exception {
        String source = "1h30m";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(5400000, milliseconds);
    }

    public void testSTimePattern2() throws Exception {
        String source = "15sec";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(15000, milliseconds);
    }

    public void testMTimePattern2() throws Exception {
        String source = "5min";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(300000, milliseconds);
    }
    
    public void testMTimePattern3() throws Exception {
        String source = "5MIN";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(300000, milliseconds);
    }
    
    public void testMSTimePattern2() throws Exception {
        String source = "4min2sec";
        long milliseconds = TimePatternConverter.toMilliSeconds(source);
        assertEquals(242000, milliseconds);
    }    
    
    //Negative Tests    
    public void testIllegalHMSTimePattern() {
        String source = "36h88m5s";
        checkForIllegalArgument(source, "Minutes should contain a valid value between 0 and 59: " + source);
    }

    public void testHoursTwice() {
        String source = "36h12hours";
        String expectedMessage = "Hours should not be specified more then once: " + source;
        checkForIllegalArgument(source, expectedMessage);
    }

    public void testMinutesTwice() {
        String source = "36minute12min";
        String expectedMessage = "Minutes should not be specified more then once: " + source;
        checkForIllegalArgument(source, expectedMessage);
    }

    public void testSecondsTwice() {
        String source = "36sec12second";
        String expectedMessage = "Seconds should not be specified more then once: " + source;
        checkForIllegalArgument(source, expectedMessage);
    }

    public void testIllegalMSTimePattern() {
        String source = "55m75s";
        checkForIllegalArgument(source, "Seconds should contain a valid value between 0 and 59: " + source);
    }

    public void testIllegalHMTimePattern() throws Exception {
        String source = "1h89s";
        checkForIllegalArgument(source, "Seconds should contain a valid value between 0 and 59: " + source);
    }

    public void testIllegalCharacters() throws Exception {
        String source = "5ssegegegegqergerg";
        checkForIllegalArgument(source, "Illegal characters: " + source);
    }

    public void testSsCharacters() throws Exception {
        String source = "5ss";
        checkForIllegalArgument(source, "Illegal characters: " + source);
    }

    private void checkForIllegalArgument(String source, String expectedMessage) {
        try {
            TimePatternConverter.toMilliSeconds(source);
            fail("Should throw IllegalArgumentException");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e);
            assertThat(e.getMessage(), is(expectedMessage));
        }
    }

}
