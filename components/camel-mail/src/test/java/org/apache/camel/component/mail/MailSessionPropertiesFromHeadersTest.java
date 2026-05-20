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

import org.apache.camel.Exchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies that dynamic JavaMail session properties provided as {@code mail.smtp.*} / {@code mail.smtps.*} message
 * headers only override the endpoint configuration when explicitly opted-in via
 * {@code useJavaMailSessionPropertiesFromHeaders=true} (CAMEL-23522).
 */
public class MailSessionPropertiesFromHeadersTest extends CamelTestSupport {

    private MailProducer producer(String uri) throws Exception {
        MailEndpoint endpoint = context.getEndpoint(uri, MailEndpoint.class);
        return (MailProducer) endpoint.createProducer();
    }

    private Exchange withSmtpHostHeader(MailProducer producer, String header) {
        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.getMessage().setHeader(header, "attacker-smtp.example.com");
        return exchange;
    }

    @Test
    public void testHeadersIgnoredByDefault() throws Exception {
        MailProducer producer = producer("smtp://user@myhost:25?password=secret");

        // no special headers -> default sender
        Exchange plain = producer.getEndpoint().createExchange();
        JavaMailSender defaultSender = producer.getSender(plain);

        // mail.smtp.* header present, but feature is disabled by default -> still the default sender
        JavaMailSender withSmtp = producer.getSender(withSmtpHostHeader(producer, "mail.smtp.host"));
        assertSame(defaultSender, withSmtp, "mail.smtp.* header must be ignored when feature is disabled (default)");

        JavaMailSender withSmtps = producer.getSender(withSmtpHostHeader(producer, "mail.smtps.host"));
        assertSame(defaultSender, withSmtps, "mail.smtps.* header must be ignored when feature is disabled (default)");
    }

    @Test
    public void testHeadersHonouredWhenOptedIn() throws Exception {
        MailProducer producer
                = producer("smtp://user@myhost:25?password=secret&useJavaMailSessionPropertiesFromHeaders=true");

        Exchange plain = producer.getEndpoint().createExchange();
        JavaMailSender defaultSender = producer.getSender(plain);

        // with the opt-in enabled, a mail.smtp.* header creates a per-message custom sender
        JavaMailSender withSmtp = producer.getSender(withSmtpHostHeader(producer, "mail.smtp.host"));
        assertNotSame(defaultSender, withSmtp, "mail.smtp.* header must create a custom sender when opted-in");

        // and the same applies to the mail.smtps.* fallback prefix
        JavaMailSender withSmtps = producer.getSender(withSmtpHostHeader(producer, "mail.smtps.host"));
        assertNotSame(defaultSender, withSmtps, "mail.smtps.* header must create a custom sender when opted-in");
    }

    @Test
    public void testNoHeadersAlwaysUsesDefaultSenderWhenOptedIn() throws Exception {
        MailProducer producer
                = producer("smtp://user@myhost:25?password=secret&useJavaMailSessionPropertiesFromHeaders=true");

        Exchange a = producer.getEndpoint().createExchange();
        Exchange b = producer.getEndpoint().createExchange();
        // no mail.smtp.* headers at all -> default sender even when the feature is enabled
        assertSame(producer.getSender(a), producer.getSender(b));
    }
}
