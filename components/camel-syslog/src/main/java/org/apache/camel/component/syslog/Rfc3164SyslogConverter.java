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

package org.apache.camel.component.syslog;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Converter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Rfc3164SyslogConverter {

    private static final transient Log LOG = LogFactory.getLog(Rfc3164SyslogConverter.class);

    private static enum MONTHS {
        jan, feb, mar, apr, may, jun, jul, aug, sep, oct, nov, dec
    }

    private final static Map<String, MONTHS> MONTH_VALUE_MAP = new HashMap<String, MONTHS>() {
        {
            put("jan", MONTHS.jan);
            put("feb", MONTHS.feb);
            put("mar", MONTHS.mar);
            put("apr", MONTHS.apr);
            put("may", MONTHS.may);
            put("jun", MONTHS.jun);
            put("jul", MONTHS.jul);
            put("aug", MONTHS.aug);
            put("sep", MONTHS.sep);
            put("oct", MONTHS.oct);
            put("nov", MONTHS.nov);
            put("dec", MONTHS.dec);
        }
    };

    @Converter
    public static String toString(SyslogMessage message) {
        StringBuilder sbr = new StringBuilder();
        sbr.append("<");
        if (message.getFacility() == null) {
            message.setFacility(SyslogFacility.USER);
        }
        if (message.getSeverity() == null) {
            message.setSeverity(SyslogSeverity.INFO);
        }
        if (message.getHostname() == null) {
            //This is massively ugly..
            try {
                message.setHostname(InetAddress.getLocalHost().toString());
            } catch (UnknownHostException e) {
                message.setHostname("UNKNOWN_HOST");
            }
        }
        sbr.append(message.getFacility().ordinal() * 8 + message.getSeverity().ordinal());
        sbr.append(">");
        if (message.getTimestamp() == null) {
            message.setTimestamp(new Date());
        }

        //SDF isn't going to help much here.

        Calendar cal = GregorianCalendar.getInstance();
        cal.setTime(message.getTimestamp());

        String firstLetter = MONTHS.values()[cal.get(Calendar.MONTH)].toString().substring(0, 1);  // Get first letter
        String remainder = MONTHS.values()[cal.get(Calendar.MONTH)].toString()
            .substring(1);    // Get remainder of word.
        String capitalized = firstLetter.toUpperCase() + remainder.toLowerCase();

        sbr.append(capitalized);
        sbr.append(" ");

        if (cal.get(Calendar.DAY_OF_MONTH) < 10) {
            sbr.append(" ").append(cal.get(Calendar.DAY_OF_MONTH));
        } else {
            sbr.append(cal.get(Calendar.DAY_OF_MONTH));
        }

        sbr.append(" ");

        if (cal.get(Calendar.HOUR_OF_DAY) < 10) {
            sbr.append("0").append(cal.get(Calendar.HOUR_OF_DAY));
        } else {
            sbr.append(cal.get(Calendar.HOUR_OF_DAY));
        }
        sbr.append(":");

        if (cal.get(Calendar.MINUTE) < 10) {
            sbr.append("0").append(cal.get(Calendar.MINUTE));
        } else {
            sbr.append(cal.get(Calendar.MINUTE));
        }
        sbr.append(":");

        if (cal.get(Calendar.SECOND) < 10) {
            sbr.append("0").append(cal.get(Calendar.SECOND));
        } else {
            sbr.append(cal.get(Calendar.SECOND));
        }
        sbr.append(" ");

        sbr.append(message.getHostname());
        sbr.append(" ");
        sbr.append(message.getLogMessage());
        return sbr.toString();
    }

    @Converter
    public static SyslogMessage toSyslogMessage(String body) {
        return parseMessage(body.getBytes());
    }

    public final static SyslogMessage parseMessage(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
        byteBuffer.put(bytes);
        byteBuffer.rewind();

        SyslogMessage syslogMessage = new SyslogMessage();
        Character charFound = (char) byteBuffer.get();

        while (charFound != '<') {
            //Ignore noise in beginning of message.
            charFound = (char) byteBuffer.get();
        }
        char priChar = 0;
        if (charFound == '<') {
            int facility = 0;

            while (Character.isDigit(priChar = (char) (byteBuffer.get() & 0xff))) {
                facility *= 10;
                facility += Character.digit(priChar, 10);
            }
            syslogMessage.setFacility(SyslogFacility.values()[facility >> 3]);
            syslogMessage.setSeverity(SyslogSeverity.values()[facility & 0x07]);
        }

        if (priChar != '>') {
            //Invalid character - this is not a well defined syslog message.
            LOG.error("Invalid syslog message, missing a > in the Facility/Priority part");
        }

        //Done parsing severity and facility
        //<169>Oct 22 10:52:01 TZ-6 scapegoat.dmz.example.org 10.1.2.3 sched[0]: That's All Folks!
        //Need to parse the date.

        /**
         The TIMESTAMP field is the local time and is in the format of "Mmm dd
         hh:mm:ss" (without the quote marks) where:

         Mmm is the English language abbreviation for the month of the
         year with the first character in uppercase and the other two
         characters in lowercase.  The following are the only acceptable
         values:

         Jan, Feb, Mar, Apr, May, Jun, Jul, Aug, Sep, Oct, Nov, Dec

         dd is the day of the month.  If the day of the month is less
         than 10, then it MUST be represented as a space and then the
         number.  For example, the 7th day of August would be
         represented as "Aug  7", with two spaces between the "g" and
         the "7".

         hh:mm:ss is the local time.  The hour (hh) is represented in a
         24-hour format.  Valid entries are between 00 and 23,
         inclusive.  The minute (mm) and second (ss) entries are between
         00 and 59 inclusive.


         */

        char[] month = new char[3];
        for (int i = 0; i < 3; i++) {
            month[i] = (char) (byteBuffer.get() & 0xff);
        }
        charFound = (char) byteBuffer.get();
        if (charFound != ' ') {
            //Invalid Message - missing mandatory space.
            LOG.error("Invalid syslog message, missing a mandatory space after month");
        }
        charFound = (char) (byteBuffer.get() & 0xff);

        int day = 0;
        if (charFound == ' ') {
            //Extra space for the day - this is okay.
            //Just ignored per the spec.
        } else {
            day *= 10;
            day += Character.digit(charFound, 10);
        }

        while (Character.isDigit(charFound = (char) (byteBuffer.get() & 0xff))) {
            day *= 10;
            day += Character.digit(charFound, 10);
        }

        int hour = 0;
        while (Character.isDigit(charFound = (char) (byteBuffer.get() & 0xff))) {
            hour *= 10;
            hour += Character.digit(charFound, 10);
        }

        int minute = 0;
        while (Character.isDigit(charFound = (char) (byteBuffer.get() & 0xff))) {
            minute *= 10;
            minute += Character.digit(charFound, 10);
        }

        int second = 0;
        while (Character.isDigit(charFound = (char) (byteBuffer.get() & 0xff))) {
            second *= 10;
            second += Character.digit(charFound, 10);
        }

        //The host is the char sequence until the next ' '

        StringBuilder host = new StringBuilder();
        while ((charFound = (char) (byteBuffer.get() & 0xff)) != ' ') {
            host.append(charFound);
        }

        syslogMessage.setHostname(host.toString());

        StringBuilder msg = new StringBuilder();
        while (byteBuffer.hasRemaining()) {
            charFound = (char) (byteBuffer.get() & 0xff);
            msg.append(charFound);
        }

        Calendar calendar = new GregorianCalendar();
        calendar.set(Calendar.MONTH, MONTH_VALUE_MAP.get((String.valueOf(month).toLowerCase())).ordinal());
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, second);

        syslogMessage.setTimestamp(calendar.getTime());

        syslogMessage.setLogMessage(msg.toString());
        if (LOG.isTraceEnabled()) {
            LOG.trace("Syslog message : " + syslogMessage.toString());
        }

        return syslogMessage;
    }
}
