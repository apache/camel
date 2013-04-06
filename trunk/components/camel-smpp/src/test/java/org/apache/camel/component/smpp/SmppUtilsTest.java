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
package org.apache.camel.component.smpp;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.jsmpp.bean.Alphabet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SmppUtilsTest {
    
    private static TimeZone defaultTimeZone;
    
    @BeforeClass
    public static void setUpBeforeClass() {
        defaultTimeZone = TimeZone.getDefault();
        
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
    }
    
    @AfterClass
    public static void tearDownAfterClass() {
        if (defaultTimeZone != null) {
            TimeZone.setDefault(defaultTimeZone);            
        }
    }

    @Test
    public void formatTime() {
        assertEquals("-300101000000000-", SmppUtils.formatTime(new Date(0L)));
        assertEquals("-300101024640000-", SmppUtils.formatTime(new Date(10000000L)));
    }
    
    @Test
    public void string2Date() {
        Date date = SmppUtils.string2Date("-300101010000004+");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        
        assertEquals(5, calendar.get(Calendar.YEAR));
        assertEquals(11, calendar.get(Calendar.MONTH));
        assertEquals(10, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(10, calendar.get(Calendar.HOUR));
        assertEquals(10, calendar.get(Calendar.MINUTE));
        assertEquals(0, calendar.get(Calendar.SECOND));
    }
    
    @Test
    public void parseAlphabetFromDataCoding() {
        assertEquals(Alphabet.ALPHA_DEFAULT, SmppUtils.parseAlphabetFromDataCoding((byte) 0x00));
        assertEquals(Alphabet.ALPHA_DEFAULT, SmppUtils.parseAlphabetFromDataCoding((byte) 0x01));
        assertEquals(Alphabet.ALPHA_DEFAULT, SmppUtils.parseAlphabetFromDataCoding((byte) 0x03));

        assertEquals(Alphabet.ALPHA_8_BIT, SmppUtils.parseAlphabetFromDataCoding((byte) 0x02));
        assertEquals(Alphabet.ALPHA_8_BIT, SmppUtils.parseAlphabetFromDataCoding((byte) 0x04));
        assertEquals(Alphabet.ALPHA_8_BIT, SmppUtils.parseAlphabetFromDataCoding((byte) 0x05));
        assertEquals(Alphabet.ALPHA_8_BIT, SmppUtils.parseAlphabetFromDataCoding((byte) 0x07));

        assertEquals(Alphabet.ALPHA_UCS2, SmppUtils.parseAlphabetFromDataCoding((byte) 0x08));
        assertEquals(Alphabet.ALPHA_UCS2, SmppUtils.parseAlphabetFromDataCoding((byte) 0x09));
        assertEquals(Alphabet.ALPHA_UCS2, SmppUtils.parseAlphabetFromDataCoding((byte) 0x0b));

        assertEquals(Alphabet.ALPHA_RESERVED, SmppUtils.parseAlphabetFromDataCoding((byte) 0x0c));
        assertEquals(Alphabet.ALPHA_RESERVED, SmppUtils.parseAlphabetFromDataCoding((byte) 0x0d));
        assertEquals(Alphabet.ALPHA_RESERVED, SmppUtils.parseAlphabetFromDataCoding((byte) 0xff));
    }
}