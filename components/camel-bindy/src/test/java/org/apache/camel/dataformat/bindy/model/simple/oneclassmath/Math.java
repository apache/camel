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
package org.apache.camel.dataformat.bindy.model.simple.oneclassmath;

import java.math.BigDecimal;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

@CsvRecord(separator = ",")
public class Math {

    @DataField(pos = 1, pattern = "00")
    private Integer intAmount;

    @DataField(pos = 2, precision = 2)
    /*
       Pattern is not yet supported by BigDecimal.
       FormatFactory class -->

               } else if (clazz == BigDecimal.class) {
            return new BigDecimalFormat(impliedDecimalSeparator, precision, getLocale(locale));

        So we should remove it from the model pattern = "00.00"
      */
    private BigDecimal bigDecimal;

    public Integer getIntAmount() {
        return intAmount;
    }

    public void setIntAmount(Integer intAmount) {
        this.intAmount = intAmount;
    }

    public BigDecimal getBigDecimal() {
        return bigDecimal;
    }

    public void setBigDecimal(BigDecimal bigDecimal) {
        this.bigDecimal = bigDecimal;
    }

    @Override
    public String toString() {
        return "intAmount : " + this.intAmount + ", " + "bigDecimal : " + this.bigDecimal;
    }
}
