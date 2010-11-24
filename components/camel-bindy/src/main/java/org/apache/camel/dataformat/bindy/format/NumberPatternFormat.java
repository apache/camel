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
import java.text.NumberFormat;
import java.util.Locale;

import org.apache.camel.dataformat.bindy.PatternFormat;
import org.apache.camel.util.ObjectHelper;

public abstract class NumberPatternFormat<T> implements PatternFormat<T> {

    private String pattern;
    private Locale locale = Locale.getDefault();

    public NumberPatternFormat() {
    }

    public NumberPatternFormat(String pattern, Locale locale) {
        this.pattern = pattern;
        this.locale = locale != null ? locale : Locale.getDefault();
    }

    public String format(T object) throws Exception {
        ObjectHelper.notNull(this.pattern, "pattern");
        return this.getNumberFormat().format(object);
    }

    @SuppressWarnings("unchecked")
    public T parse(String string) throws Exception {
        ObjectHelper.notNull(this.pattern, "pattern");
        return (T)this.getNumberFormat().parse(string);
    }

    protected NumberFormat getNumberFormat() {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        if (format instanceof DecimalFormat) {
            ((DecimalFormat)format).applyLocalizedPattern(pattern);
        }
        return format;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
}
