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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.junit.Assert;
import org.junit.Test;

public class CxfRsHeaderFilterStrategyTest extends Assert {
    @Test
    public void testFilterContentType() throws Exception {
        HeaderFilterStrategy filter = new CxfRsHeaderFilterStrategy();
        assertTrue("Get a wrong filtered result", filter.applyFilterToCamelHeaders("content-type", "just a test", null));
        assertTrue("Get a wrong filtered result", filter.applyFilterToCamelHeaders("Content-Type", "just a test", null));
    }
    
    @Test
    public void testFilterCamelHeaders() throws Exception {
        HeaderFilterStrategy filter = new CxfRsHeaderFilterStrategy();
        assertTrue("Get a wrong filtered result", filter.applyFilterToCamelHeaders(Exchange.CHARSET_NAME, "just a test", null));
        assertTrue("Get a wrong filtered result", filter.applyFilterToCamelHeaders(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, "just a test", null));
        assertTrue("Get a wrong filtered result", filter.applyFilterToCamelHeaders("org.apache.camel.such.Header", "just a test", null));
        assertFalse("Get a wrong filtered result", filter.applyFilterToCamelHeaders("camel.result", "just a test", null));
    }

}
