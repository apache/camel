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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;


public class BigDecimalFormat extends AbstractNumberFormat<BigDecimal> {

    public BigDecimalFormat(boolean impliedDecimalPosition, int precision, Locale locale) {
        super(impliedDecimalPosition, precision, locale);
    }

    public String format(BigDecimal object) throws Exception {
        return !super.hasImpliedDecimalPosition()
            ? super.getFormat().format(object)
            : super.getFormat().format(object.multiply(new BigDecimal(super.getMultiplier())));
    }

    public BigDecimal parse(String string) throws Exception {
        BigDecimal result = new BigDecimal(string.trim());
        if (super.hasImpliedDecimalPosition()) {
            result = result.divide(new BigDecimal(super.getMultiplier()), super.getPrecision(), RoundingMode.HALF_EVEN);
        } else {
            if (super.getPrecision() != -1) {
                result = result.setScale(super.getPrecision());
            }
        }
        return result;
    }
}
