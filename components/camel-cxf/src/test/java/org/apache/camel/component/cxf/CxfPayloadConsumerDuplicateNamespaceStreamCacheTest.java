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
package org.apache.camel.component.cxf;

import org.junit.Test;

public class CxfPayloadConsumerDuplicateNamespaceStreamCacheTest extends CxfPayloadConsumerNamespaceOnEnvelopeStreamCacheTest {
    /*
     * The soap namespace prefix is already defined on the root tag of the
     * payload. If this is set another time from the envelope, the result will
     * be an invalid XML.
     */
    protected static final String REQUEST_MESSAGE =
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
            + "<soap:Body><ns2:getToken xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ns2=\"http://camel.apache.org/cxf/namespace\">"
            + "<arg0 xsi:type=\"xs:string\">Send</arg0></ns2:getToken></soap:Body></soap:Envelope>";

    @Override
    @Test
    public void testInvokeRouter() {
        Object returnValue = template.requestBody("direct:router", REQUEST_MESSAGE);
        assertNotNull(returnValue);
        assertTrue(returnValue instanceof String);
        assertTrue(((String) returnValue).contains("Return Value"));
        assertTrue(((String) returnValue).contains("http://www.w3.org/2001/XMLSchema-instance"));
    }
}
