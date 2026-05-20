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

    @Test
    public void testInboundFiltersSmtpPropertyNamespace() {
        assertTrue(strategy.applyFilterToExternalHeaders("mail.smtp.host", "evil", null));
        assertTrue(strategy.applyFilterToExternalHeaders("mail.smtp.ssl.trust", "*", null));
        assertTrue(strategy.applyFilterToExternalHeaders("mail.smtps.host", "evil", null));
        // case-insensitive
        assertTrue(strategy.applyFilterToExternalHeaders("MAIL.SMTP.STARTTLS.ENABLE", "false", null));
    }

    @Test
    public void testInboundStillFiltersCamelHeaders() {
        assertTrue(strategy.applyFilterToExternalHeaders("CamelFileName", "x", null));
        assertTrue(strategy.applyFilterToExternalHeaders("camelfilename", "x", null));
    }

    @Test
    public void testInboundLetsOrdinaryMailHeadersThrough() {
        assertFalse(strategy.applyFilterToExternalHeaders("Subject", "Hello", null));
        assertFalse(strategy.applyFilterToExternalHeaders("To", "user@host.com", null));
        // a header that merely contains, but does not start with, the namespace must pass
        assertFalse(strategy.applyFilterToExternalHeaders("X-mail.smtp.host", "x", null));
    }

    @Test
    public void testOutboundIsUnaffectedBySmtpNamespaceFilter() {
        // the inbound-only filter must not change the outbound behaviour; only Camel* is filtered outbound
        assertFalse(strategy.applyFilterToCamelHeaders("mail.smtp.host", "myhost", null));
        assertTrue(strategy.applyFilterToCamelHeaders("CamelFileName", "x", null));
    }
}
