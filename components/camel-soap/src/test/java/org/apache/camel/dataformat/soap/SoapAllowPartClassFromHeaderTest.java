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
package org.apache.camel.dataformat.soap;

import org.apache.camel.reifier.dataformat.DataFormatReifier;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SoapAllowPartClassFromHeaderTest extends CamelTestSupport {

    private static final String CONTEXT_PATH = "com.example.customerservice";

    @Test
    public void testAllowPartClassFromHeaderIsOffByDefault() {
        DataFormat df = DataFormatReifier.getDataFormat(context,
                new org.apache.camel.model.dataformat.SoapDataFormat.Builder().contextPath(CONTEXT_PATH).end());

        SoapDataFormat soap = assertInstanceOf(SoapDataFormat.class, df);
        assertFalse(soap.isAllowPartClassFromHeader());
    }

    @Test
    public void testAllowPartClassFromHeaderFromModel() {
        DataFormat df = DataFormatReifier.getDataFormat(context,
                new org.apache.camel.model.dataformat.SoapDataFormat.Builder().contextPath(CONTEXT_PATH)
                        .allowPartClassFromHeader(true).end());

        SoapDataFormat soap = assertInstanceOf(SoapDataFormat.class, df);
        assertTrue(soap.isAllowPartClassFromHeader());
    }
}
