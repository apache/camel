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

import org.apache.camel.spi.Metadata;

public final class WhatsAppConstants {

    @Metadata(label = "producer", description = "Phone Number ID taken from WhatsApp Meta for Developers Dashboard",
              javaType = "Object",
              deprecationNote = "Never read by the component: the phone number ID comes from the endpoint URI, as the"
                                + " phoneNumberId path parameter")
    @Deprecated
    public static final String WHATSAPP_PHONE_NUMBER_ID = "CamelWhatsAppPhoneNumberId";

    @Metadata(label = "producer", description = "Recipient phone number associated with Phone Number ID", javaType = "Object",
              deprecationNote = "Never read by the component: the recipient comes from the to field of the BaseMessage the body"
                                + " is converted to")
    @Deprecated
    public static final String WHATSAPP_RECIPIENT_PHONE_NUMBER_ID = "CamelWhatsAppRecipientPhoneNumberId";

    private WhatsAppConstants() {
    }
}
