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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CxfRsHeaderFilterStrategyTest {
    @Test
    public void testFilterContentType() throws Exception {
        HeaderFilterStrategy filter = new CxfRsHeaderFilterStrategy();
        assertTrue(filter.applyFilterToCamelHeaders("content-type", "just a test", null), "Get a wrong filtered result");
        assertTrue(filter.applyFilterToCamelHeaders("Content-Type", "just a test", null), "Get a wrong filtered result");
    }

    @Test
    public void testFilterCamelHeaders() throws Exception {
        HeaderFilterStrategy filter = new CxfRsHeaderFilterStrategy();
        assertTrue(filter.applyFilterToCamelHeaders(Exchange.CHARSET_NAME, "just a test", null), "Get a wrong filtered result");
        assertTrue(filter.applyFilterToCamelHeaders(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, "just a test", null),
                "Get a wrong filtered result");
        assertTrue(filter.applyFilterToCamelHeaders("org.apache.camel.such.Header", "just a test", null),
                "Get a wrong filtered result");
        assertTrue(filter.applyFilterToCamelHeaders("camel.result", "just a test", null), "Get a wrong filtered result");

        assertFalse(filter.applyFilterToCamelHeaders("MyWorld", "just a test", null), "Get a wrong filtered result");
    }

}
