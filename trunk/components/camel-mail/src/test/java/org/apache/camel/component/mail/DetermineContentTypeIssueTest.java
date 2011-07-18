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
package org.apache.camel.component.mail;

import org.apache.camel.Exchange;
import org.apache.camel.test.junit4.ExchangeTestSupport;
import org.junit.Test;

/**
 *
 */
public class DetermineContentTypeIssueTest extends ExchangeTestSupport {

    private final String contentType = "application/pkcs7-mime; smime-type=enveloped-data; name=\"smime.p7m\"";

    @Test
    public void testDetermineContentTypeNoChange() throws Exception {
        MailConfiguration configuration = new MailConfiguration();
        MailBinding binding = new MailBinding();

        exchange.getIn().setHeader("Content-Type", contentType);
        String determinedType = binding.determineContentType(configuration, exchange);

        // no charset
        assertEquals(contentType, determinedType);
    }

    @Test
    public void testDetermineContentTypeCharSetFromExchange() throws Exception {
        MailConfiguration configuration = new MailConfiguration();
        MailBinding binding = new MailBinding();

        exchange.getIn().setHeader("Content-Type", contentType);
        exchange.setProperty(Exchange.CHARSET_NAME, "iso-8859-1");

        String determinedType = binding.determineContentType(configuration, exchange);

        String expected = contentType + "; charset=iso-8859-1";

        // should append the charset from exchange
        assertEquals(expected, determinedType);
    }

    @Test
    public void testDetermineContentTypeFallbackCharSetFromExchange() throws Exception {
        MailConfiguration configuration = new MailConfiguration();
        MailBinding binding = new MailBinding();

        String type = contentType + "; charset=utf-8";
        exchange.getIn().setHeader("Content-Type", type);
        exchange.setProperty(Exchange.CHARSET_NAME, "iso-8859-1");

        String determinedType = binding.determineContentType(configuration, exchange);

        // should keep existing charset
        assertEquals(type, determinedType);
    }

    @Test
    public void testDetermineContentTypeIgnoreUnsupportedExchangeAsFallback() throws Exception {
        MailConfiguration configuration = new MailConfiguration();
        // ignore unsupported
        configuration.setIgnoreUnsupportedCharset(true);
        MailBinding binding = new MailBinding();

        String type = contentType + "; charset=ansi_x3.110-1983";
        exchange.getIn().setHeader("Content-Type", type);
        exchange.setProperty(Exchange.CHARSET_NAME, "iso-8859-1");

        String determinedType = binding.determineContentType(configuration, exchange);

        // should remove unsupported charset
        assertEquals(contentType, determinedType);
    }

    @Test
    public void testDetermineContentTypeInvalidCharset() throws Exception {
        MailConfiguration configuration = new MailConfiguration();
        MailBinding binding = new MailBinding();

        String type = contentType + "; charset=ansi_x3.110-1983";
        exchange.getIn().setHeader("Content-Type", type);

        String determinedType = binding.determineContentType(configuration, exchange);

        // should keep existing charset even if its unsupported as we configured it as that
        assertEquals(type, determinedType);
    }

    @Test
    public void testDetermineContentTypeWithCharsetInMiddle() throws Exception {
        MailConfiguration configuration = new MailConfiguration();
        MailBinding binding = new MailBinding();

        String type = "text/plain; charset=iso-8859-1; foo=bar";

        exchange.getIn().setHeader("Content-Type", type);
        String determinedType = binding.determineContentType(configuration, exchange);

        // content-type is left untouched
        assertEquals(type, determinedType);
    }

}
