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
package org.apache.camel.util;

import java.text.DecimalFormatSymbols;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.camel.util.UnitUtils.printUnitFromBytes;

public class UnitUtilsTest extends Assert {

    @Test
    public void testPrintUnitFromBytes() throws Exception {

        // needed for the locales that have a decimal separator other than comma
        char decimalSeparator = DecimalFormatSymbols.getInstance().getDecimalSeparator();

        assertEquals("999 B", printUnitFromBytes(999));
        assertEquals("1" + decimalSeparator + "0 kB", printUnitFromBytes(1000));
        assertEquals("1" + decimalSeparator + "0 kB", printUnitFromBytes(1001));

        assertEquals("1000" + decimalSeparator + "0 kB", printUnitFromBytes(999999));
        assertEquals("1" + decimalSeparator + "0 MB", printUnitFromBytes(1000000));
        assertEquals("1" + decimalSeparator + "0 MB", printUnitFromBytes(1000001));

        assertEquals("1" + decimalSeparator + "5 MB", printUnitFromBytes(1500001));
    }
}
