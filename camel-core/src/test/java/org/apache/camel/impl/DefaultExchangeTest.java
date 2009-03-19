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
package org.apache.camel.impl;

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoTypeConversionAvailableException;

/**
 * @version $Revision$
 */
public class DefaultExchangeTest extends ExchangeTestSupport {

    public void testBody() throws Exception {
        assertNotNull(exchange.getIn().getBody());

        assertEquals("<hello id='m123'>world!</hello>", exchange.getIn().getBody());
        assertEquals("<hello id='m123'>world!</hello>", exchange.getIn().getBody(String.class));

        assertEquals("<hello id='m123'>world!</hello>", exchange.getIn().getMandatoryBody());
        assertEquals("<hello id='m123'>world!</hello>", exchange.getIn().getMandatoryBody(String.class));
    }

    public void testMandatoryBody() throws Exception {
        assertNotNull(exchange.getIn().getBody());

        assertEquals("<hello id='m123'>world!</hello>", exchange.getIn().getBody());
        assertEquals(null, exchange.getIn().getBody(Integer.class));

        assertEquals("<hello id='m123'>world!</hello>", exchange.getIn().getMandatoryBody());
        try {
            exchange.getIn().getMandatoryBody(Integer.class);
            fail("Should have thrown an InvalidPayloadException");
        } catch (InvalidPayloadException e) {
            // expected
        }
    }

    public void testHeader() throws Exception {
        assertNotNull(exchange.getIn().getHeaders());

        assertEquals(123, exchange.getIn().getHeader("bar"));
        assertEquals(new Integer(123), exchange.getIn().getHeader("bar", Integer.class));
        assertEquals("123", exchange.getIn().getHeader("bar", String.class));
    }

}
