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
package org.apache.camel.component.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link MailHeaderFilterStrategy} blocks the Camel-internal {@code mail.smtp.*} / {@code mail.smtps.*}
 * namespace on the inbound path (CAMEL-23522) while still letting ordinary mail headers through, and that the existing
 * {@code Camel*} filtering (CAMEL-23222) is preserved.
 */
public class MailHeaderFilterStrategyTest {

    private final MailHeaderFilterStrategy strategy = new MailHeaderFilterStrategy();

    // additional 3.14.10 tests
    @Test
    void filtersMixedCaseCamelHeaders() {
        assertTrue(strategy.applyFilterToCamelHeaders("CAmeLFileName", "test.txt", null));
        assertTrue(strategy.applyFilterToCamelHeaders("CAMELHttpMethod", "GET", null));
        assertTrue(strategy.applyFilterToCamelHeaders("cAmElVersion", "3.14", null));
        assertTrue(strategy.applyFilterToCamelHeaders("ORg.Apache.Camel.", "value", null));
    }

    @Test
    void inboundFiltersMixedCaseCamelHeaders() {
        assertTrue(strategy.applyFilterToExternalHeaders("CAmeLFileName", "test.txt", null));
        assertTrue(strategy.applyFilterToExternalHeaders("CAMELHttpMethod", "GET", null));
        assertFalse(strategy.applyFilterToExternalHeaders("myHeader", "value", null));
        assertTrue(strategy.applyFilterToExternalHeaders("ORg.Apache.Camel.", "value", null));
    }

    // 4.x
    @Test
    public void testInboundFiltersSmtpPropertyNamespace() {
        assertTrue(strategy.applyFilterToExternalHeaders("mail.smtp.host", "evil", null));
        assertTrue(strategy.applyFilterToExternalHeaders("mail.smtp.ssl.trust", "*", null));
        assertTrue(strategy.applyFilterToExternalHeaders("mail.smtps.host", "evil", null));
        // case-insensitive
        assertTrue(strategy.applyFilterToExternalHeaders("MAIL.SMTP.STARTTLS.ENABLE", "false", null));
        // mixed-case
        assertTrue(strategy.applyFilterToExternalHeaders("MAil.SmTp.STARTTLS.ENABLE", "false", null));
    }

    @Test
    public void testInboundStillFiltersCamelHeaders() {
        assertTrue(strategy.applyFilterToExternalHeaders("CamelFileName", "x", null));
        assertTrue(strategy.applyFilterToExternalHeaders("camelfilename", "x", null));
        assertTrue(strategy.applyFilterToExternalHeaders("org.apache.camel.", "value", null));
    }

    @Test
    public void testInboundLetsOrdinaryMailHeadersThrough() {
        assertFalse(strategy.applyFilterToExternalHeaders("Subject", "Hello", null));
        assertFalse(strategy.applyFilterToExternalHeaders("To", "user@host.com", null));
        // a header that merely contains, but does not start with, the namespace must pass
        assertFalse(strategy.applyFilterToExternalHeaders("X-mail.smtp.host", "x", null));
        assertFalse(strategy.applyFilterToExternalHeaders("orgapachecamel.", "value", null));
    }

    @Test
    public void testOutboundIsUnaffectedBySmtpNamespaceFilter() {
        // the inbound-only filter must not change the outbound behaviour; only Camel* is filtered outbound
        assertFalse(strategy.applyFilterToCamelHeaders("mail.smtp.host", "myhost", null));
        assertTrue(strategy.applyFilterToCamelHeaders("CamelFileName", "x", null));
    }
}