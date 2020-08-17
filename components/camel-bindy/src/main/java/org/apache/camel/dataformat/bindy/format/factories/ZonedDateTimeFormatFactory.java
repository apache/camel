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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.camel.dataformat.bindy.Format;
import org.apache.camel.dataformat.bindy.FormattingOptions;
import org.apache.camel.dataformat.bindy.PatternFormat;
import org.apache.camel.util.ObjectHelper;

public class ZonedDateTimeFormatFactory extends AbstractFormatFactory {

    {
        supportedClasses.add(ZonedDateTime.class);
    }

    @Override
    public Format<?> build(FormattingOptions formattingOptions) {
        return new ZonedDateTimePatternFormat(
                formattingOptions.getPattern(),
                formattingOptions.getLocale());
    }

    private static class ZonedDateTimePatternFormat implements PatternFormat<ZonedDateTime> {

        private String pattern;
        private Locale locale;

        ZonedDateTimePatternFormat(String pattern, Locale locale) {
            this.pattern = pattern;
            this.locale = locale;
        }

        @Override
        public String format(ZonedDateTime object) throws Exception {
            ObjectHelper.notNull(this.pattern, "pattern");
            return this.getDateFormat().format(object);
        }

        @Override
        public ZonedDateTime parse(String string) throws Exception {

            DateTimeFormatter df = this.getDateFormat();

            ObjectHelper.notNull(this.pattern, "pattern");

            return ZonedDateTime.parse(string, df);
        }

        DateTimeFormatter getDateFormat() {
            DateTimeFormatter result;
            if (locale != null) {
                result = DateTimeFormatter.ofPattern(pattern, locale);
            } else {
                result = DateTimeFormatter.ofPattern(pattern);
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
