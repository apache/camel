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
package org.apache.camel.dataformat.bindy;

import java.util.Locale;

import org.apache.camel.dataformat.bindy.annotation.BindyConverter;
import org.apache.camel.util.ObjectHelper;

public class FormattingOptions {
    private String pattern;
    private Locale locale;
    private String timezone;
    private int precision;
    private String rounding;
    private boolean impliedDecimalSeparator;
    private String decimalSeparator;
    private String groupingSeparator;
    private Class<?> clazz;
    private BindyConverter bindyConverter;

    public String getPattern() {
        return pattern;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getTimezone() {
        return timezone;
    }

    public int getPrecision() {
        return precision;
    }

    public String getRounding() {
        return rounding;
    }

    public boolean isImpliedDecimalSeparator() {
        return impliedDecimalSeparator;
    }

    public String getDecimalSeparator() {
        return decimalSeparator;
    }

    public String getGroupingSeparator() {
        return groupingSeparator;
    }

    public FormattingOptions withPattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    public FormattingOptions withLocale(String locale) {
        this.locale = getLocale(locale);
        return this;
    }

    public FormattingOptions withTimezone(String timezone) {
        this.timezone = timezone;
        return this;
    }

    public FormattingOptions withPrecision(int precision) {
        this.precision = precision;
        return this;
    }

    public FormattingOptions withRounding(String rounding) {
        this.rounding = rounding;
        return this;
    }

    public FormattingOptions withImpliedDecimalSeparator(boolean impliedDecimalSeparator) {
        this.impliedDecimalSeparator = impliedDecimalSeparator;
        return this;
    }

    public FormattingOptions withDecimalSeparator(String decimalSeparator) {
        this.decimalSeparator = decimalSeparator;
        return this;
    }

    public FormattingOptions withGroupingSeparator(String groupingSeparator) {
        this.groupingSeparator = groupingSeparator;
        return this;
    }

    public FormattingOptions forClazz(Class<?> clazz) {
        this.clazz = clazz;
        return this;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    private Locale getLocale(String locale) {
        if ("default".equals(locale)) {
            return Locale.getDefault();
        }

        Locale answer = null;
        if (ObjectHelper.isNotEmpty(locale)) {
            String[] result = locale.split("-");
            if (result.length <= 2) {
                answer = result.length == 1 ? new Locale(result[0]) : new Locale(result[0], result[1]);
            }
        }
        return answer;
    }

    public FormattingOptions withBindyConverter(BindyConverter bindyConverter) {
        this.bindyConverter = bindyConverter;
        return this;
    }

    public BindyConverter getBindyConverter() {
        return bindyConverter;
    }
}
