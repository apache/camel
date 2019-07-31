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

import java.util.Locale;

import org.apache.camel.dataformat.bindy.Format;
import org.apache.camel.dataformat.bindy.FormattingOptions;
import org.apache.camel.dataformat.bindy.format.NumberPatternFormat;
import org.apache.camel.util.ObjectHelper;

public class DoublePatternFormatFactory extends AbstractFormatFactory {

    {
        supportedClasses.add(double.class);
        supportedClasses.add(Double.class);
    }

    @Override
    public boolean canBuild(FormattingOptions formattingOptions) {
        return super.canBuild(formattingOptions)
                && ObjectHelper.isNotEmpty(formattingOptions.getPattern());
    }

    @Override
    public Format<?> build(FormattingOptions formattingOptions) {
        return new DoublePatternFormat(formattingOptions.getPattern(),
                formattingOptions.getLocale());
    }

    private static class DoublePatternFormat extends NumberPatternFormat<Double> {

        DoublePatternFormat(String pattern, Locale locale) {
            super(pattern, locale);
        }

        @Override
        public Double parse(String string) throws Exception {
            if (getNumberFormat() != null) {
                return getNumberFormat().parse(string).doubleValue();
            } else {
                return Double.valueOf(string);
            }
        }

    }

}
