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
package org.apache.camel.component.whatsapp;

import org.apache.camel.CamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;

public class WhatsAppTestSupport extends CamelTestSupport {

    protected static final String VERIFY_TOKEN = "111";
    protected static final String WRONG_VERIFY_TOKEN = "222";

    protected String phoneNumberId;
    protected String recipientPhoneNumber;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        final WhatsAppComponent component = new WhatsAppComponent();

        WhatsAppApiConfig apiConfig = getWhatsAppApiConfig();
        component.setBaseUri(apiConfig.getBaseUri());
        component.setAuthorizationToken(apiConfig.getAuthorizationToken());
        component.setWebhookVerifyToken(VERIFY_TOKEN);
        this.phoneNumberId = apiConfig.getPhoneNumberId();
        this.recipientPhoneNumber = apiConfig.getRecipientPhoneNumber();

        context.addComponent("whatsapp", component);
        return context;
    }

    protected WhatsAppApiConfig getWhatsAppApiConfig() {
        return WhatsAppApiConfig.fromEnv();
    }
}
