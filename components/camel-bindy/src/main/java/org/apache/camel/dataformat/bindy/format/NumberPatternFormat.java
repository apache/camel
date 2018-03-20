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
package org.apache.camel.dataformat.bindy.format;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import org.apache.camel.dataformat.bindy.PatternFormat;

public abstract class NumberPatternFormat<T> implements PatternFormat<T> {

    private String pattern;
    private Locale locale;
    private String decimalSeparator;
    private String groupingSeparator;
    private int precision;
    private String rounding;

    public NumberPatternFormat() {
    }

    public NumberPatternFormat(String pattern, Locale locale) {
        this.pattern = pattern;
        this.locale = locale;
    }

    public NumberPatternFormat(String pattern, Locale locale, int precision, String rounding, String decimalSeparator, String groupingSeparator) {
        this.pattern = pattern;
        this.locale = locale;
        this.decimalSeparator = decimalSeparator;
        this.groupingSeparator = groupingSeparator;
        this.precision = precision;
        this.rounding = rounding;
    }

    public String format(T object) throws Exception {
        if (getNumberFormat() != null) {
            return this.getNumberFormat().format(object);
        } else {
            return object.toString();
        }
    }

    public abstract T parse(String string) throws Exception;

    /**
     * Gets the number format if in use.
     *
     * @return the number format, or <tt>null</tt> if not in use
     */
    protected NumberFormat getNumberFormat() {
        if (locale == null) {
            return null;
        }

        NumberFormat format = NumberFormat.getNumberInstance(locale);
        if (format instanceof DecimalFormat) {
            DecimalFormat df = (DecimalFormat) format;
            if (decimalSeparator != null && groupingSeparator != null) {
                if (!decimalSeparator.isEmpty() && !groupingSeparator.isEmpty()) {
                    DecimalFormatSymbols dfs = new DecimalFormatSymbols(locale);
                    dfs.setDecimalSeparator(decimalSeparator.charAt(0));
                    dfs.setGroupingSeparator(groupingSeparator.charAt(0));
                    df.setDecimalFormatSymbols(dfs);
                }
            }
            if (!pattern.isEmpty()) {
                df.applyPattern(pattern);
            }
        }
        return format;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public String getRounding() {
        return rounding;
    }

    public void setRounding(String rounding) {
        this.rounding = rounding;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

}
