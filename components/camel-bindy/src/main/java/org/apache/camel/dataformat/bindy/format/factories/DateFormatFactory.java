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
package org.apache.camel.dataformat.bindy.format.factories;

import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.camel.dataformat.bindy.Format;
import org.apache.camel.dataformat.bindy.FormattingOptions;
import org.apache.camel.dataformat.bindy.PatternFormat;
import org.apache.camel.dataformat.bindy.format.FormatException;
import org.apache.camel.util.ObjectHelper;

public class DateFormatFactory extends AbstractFormatFactory {

    {
        supportedClasses.add(Date.class);
    }

    @Override
    public Format<?> build(FormattingOptions formattingOptions) {
        return new DatePatternFormat(
                formattingOptions.getPattern(),
                formattingOptions.getTimezone(),
                formattingOptions.getLocale());
    }

    private static class DatePatternFormat implements PatternFormat<Date> {

        private String pattern;
        private Locale locale;
        private TimeZone timezone;

        DatePatternFormat(String pattern, String timezone, Locale locale) {
            this.pattern = pattern;
            this.locale = locale;
            if (!timezone.isEmpty()) {
                this.timezone = TimeZone.getTimeZone(timezone);
            }
        }

        @Override
        public String format(Date object) throws Exception {
            ObjectHelper.notNull(this.pattern, "pattern");
            return this.getDateFormat().format(object);
        }

        @Override
        public Date parse(String string) throws Exception {

            Date date;
            DateFormat df = this.getDateFormat();

            ObjectHelper.notNull(this.pattern, "pattern");

            // Check length of the string with date pattern
            // To avoid to parse a string date : 20090901-10:32:30 when
            // the pattern is yyyyMMdd

            if (string.length() <= this.pattern.length()) {

                // Force the parser to be strict in the syntax of the date to be
                // converted
                df.setLenient(false);
                date = df.parse(string);

                return date;

            } else {
                // A formatted date can be longer than its pattern (M, d, H or h with two digits, MMMM, EEEE, a),
                // so a longer string is accepted when it is parsed completely. A date followed by other
                // characters, such as 20090901-10:32:30 with the pattern yyyyMMdd, is still rejected
                df.setLenient(false);
                ParsePosition position = new ParsePosition(0);
                date = df.parse(string, position);
                if (date == null || position.getIndex() != string.length()) {
                    throw new FormatException("Date provided does not fit the pattern defined");
                }

                return date;
            }

        }

        protected DateFormat getDateFormat() {
            SimpleDateFormat result;
            if (locale != null) {
                result = new SimpleDateFormat(pattern, locale);
            } else {
                result = new SimpleDateFormat(pattern);
            }
            if (timezone != null) {
                result.setTimeZone(timezone);
            }
            return result;
        }

        @Override
        public String getPattern() {
            return pattern;
        }

        /**
         * Sets the pattern
         *
         * @param pattern the pattern
         */
        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
    }
}
