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

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.dataformat.bindy.PatternFormat;
import org.apache.camel.util.ObjectHelper;

public class DatePatternFormat implements PatternFormat<Date> {

    private String pattern;

    public DatePatternFormat() {
    }

    public DatePatternFormat(String pattern) {
        this.pattern = pattern;
    }

    public String format(Date object) throws Exception {
        ObjectHelper.notNull(this.pattern, "pattern");
        return this.getDateFormat().format(object);
    }

    public Date parse(String string) throws Exception {
        ObjectHelper.notNull(this.pattern, "pattern");
        return this.getDateFormat().parse(string);
    }

    protected java.text.DateFormat getDateFormat() {
        return new SimpleDateFormat(this.pattern);
    }

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
