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
package org.apache.camel.dataformat.bindy.format.factories;

import java.math.BigDecimal;
import java.util.Locale;

import org.apache.camel.dataformat.bindy.Format;
import org.apache.camel.dataformat.bindy.FormattingOptions;
import org.apache.camel.dataformat.bindy.format.AbstractNumberFormat;
import org.apache.camel.util.ObjectHelper;

public class FloatFormatFactory extends AbstractFormatFactory {

    {
        supportedClasses.add(float.class);
        supportedClasses.add(Float.class);
    }

    @Override
    public boolean canBuild(FormattingOptions formattingOptions) {
        return super.canBuild(formattingOptions)
                && ObjectHelper.isEmpty(formattingOptions.getPattern());
    }

    @Override
    public Format<?> build(FormattingOptions formattingOptions) {
        return new FloatFormat(formattingOptions.isImpliedDecimalSeparator(),
                formattingOptions.getPrecision(),
                formattingOptions.getLocale());
    }

    private static class FloatFormat extends AbstractNumberFormat<Float> {

        FloatFormat(boolean impliedDecimalPosition, int precision, Locale locale) {
            super(impliedDecimalPosition, precision, locale);
        }

        public String format(Float object) throws Exception {
            return !super.hasImpliedDecimalPosition()
                    ? super.getFormat().format(object)
                    : super.getFormat().format(object * super.getMultiplier());
        }

        public Float parse(String string) throws Exception {
            Float value;
            if (!super.hasImpliedDecimalPosition()) {
                value = Float.parseFloat(string.trim());
            } else {
                BigDecimal tmp = new BigDecimal(string.trim());
                BigDecimal div = BigDecimal.valueOf(super.getMultiplier());
                value = tmp.divide(div).floatValue();
            }

            return value;
        }
    }

}
