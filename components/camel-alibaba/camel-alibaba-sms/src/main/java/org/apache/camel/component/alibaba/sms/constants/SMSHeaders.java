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
package org.apache.camel.component.alibaba.sms.constants;

import org.apache.camel.spi.Metadata;

public final class SMSHeaders {

    @Metadata(label = "producer",
              description = "The operation that the producer performs. This header replaces the operation option. If this"
                            + " header is not set, an exchange property of the same name replaces the operation option.",
              javaType = "String")
    public static final String OPERATION = SMSProperties.OPERATION;

    @Metadata(label = "producer",
              description = "The phone numbers of the recipients, which are separated by commas. Short Message Service accepts"
                            + " at most 1000 phone numbers. This header replaces the phoneNumbers option. If this header is not"
                            + " set, an exchange property of the same name replaces the phoneNumbers option.",
              javaType = "String")
    public static final String PHONE_NUMBERS = SMSProperties.PHONE_NUMBERS;

    @Metadata(label = "producer",
              description = "The sign name of the SMS message. The value must be an approved sign name. This header replaces"
                            + " the signName option. If this header is not set, an exchange property of the same name replaces"
                            + " the signName option.",
              javaType = "String")
    public static final String SIGN_NAME = SMSProperties.SIGN_NAME;

    @Metadata(label = "producer",
              description = "The template code of the SMS message. The value must be the code of an approved template. This"
                            + " header replaces the templateCode option. If this header is not set, an exchange property of the"
                            + " same name replaces the templateCode option.",
              javaType = "String")
    public static final String TEMPLATE_CODE = SMSProperties.TEMPLATE_CODE;

    @Metadata(label = "producer",
              description = "The template parameters of the SMS message as a JSON string. The JSON string must provide a value"
                            + " for each variable of the template. This header replaces the templateParam option. If this"
                            + " header is not set, an exchange property of the same name replaces the templateParam option.",
              javaType = "String")
    public static final String TEMPLATE_PARAM = SMSProperties.TEMPLATE_PARAM;

    @Metadata(label = "producer",
              description = "The external transaction ID of the SMS message. This header replaces the outId option. If this"
                            + " header is not set, an exchange property of the same name replaces the outId option.",
              javaType = "String")
    public static final String OUT_ID = SMSProperties.OUT_ID;

    @Metadata(label = "producer",
              description = "The request ID that Short Message Service returns in the response to the operation. The producer"
                            + " sets it, unless the exchange fails.",
              javaType = "String")
    public static final String REQUEST_ID = SMSProperties.REQUEST_ID;

    private SMSHeaders() {
    }
}
