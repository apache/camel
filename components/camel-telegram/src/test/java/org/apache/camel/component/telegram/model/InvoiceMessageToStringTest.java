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
package org.apache.camel.component.telegram.model;

import org.apache.camel.component.telegram.model.payments.CreateInvoiceLinkMessage;
import org.apache.camel.component.telegram.model.payments.SendInvoiceMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The producer logs the outgoing message at DEBUG level, so the toString() of an invoice must not show the provider
 * token.
 */
class InvoiceMessageToStringTest {

    @Test
    void testSendInvoiceMessageMasksTheProviderToken() {
        SendInvoiceMessage message = new SendInvoiceMessage();
        message.setTitle("Coffee");
        message.setProviderToken("284685063:TEST:secret-provider-token");

        assertThat(message.toString())
                .doesNotContain("secret-provider-token")
                .contains("providerToken='xxxxxx'")
                .contains("title='Coffee'");
    }

    @Test
    void testCreateInvoiceLinkMessageMasksTheProviderToken() {
        CreateInvoiceLinkMessage message = new CreateInvoiceLinkMessage();
        message.setTitle("Coffee");
        message.setProviderToken("284685063:TEST:secret-provider-token");

        assertThat(message.toString())
                .doesNotContain("secret-provider-token")
                .contains("providerToken='xxxxxx'")
                .contains("title='Coffee'");
    }

    @Test
    void testEmptyProviderTokenOfTelegramStarsIsShown() {
        SendInvoiceMessage message = new SendInvoiceMessage();
        message.setProviderToken("");

        assertThat(message.toString()).contains("providerToken=''");
    }
}
