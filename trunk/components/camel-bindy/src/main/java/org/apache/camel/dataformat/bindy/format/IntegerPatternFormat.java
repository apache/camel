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

import java.text.NumberFormat;
import java.util.Locale;

public class IntegerPatternFormat extends NumberPatternFormat<Integer> {

    public IntegerPatternFormat() {
    }

    public IntegerPatternFormat(String pattern, Locale locale) {
        super(pattern, locale);
    }

    @Override
    public Integer parse(String string) throws FormatException {

        Integer res = null;
        NumberFormat pat;

        // First we will test if the string can become an Integer
        try {
            res = Integer.parseInt(string);

            // Second, we will parse the string using DecimalPattern
            // to apply pattern

            pat = super.getNumberFormat();
            pat.parse(string).intValue();

        } catch (Exception ex) {
            throw new FormatException("String provided does not fit the Integer pattern defined or is not parseable");
        }

        return res;

    }

}
