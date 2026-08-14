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

    @Metadata(label = "producer", description = "Phone numbers to send SMS to", javaType = "String")
    public static final String PHONE_NUMBERS = SMSProperties.PHONE_NUMBERS;

    @Metadata(label = "producer", description = "SMS sign name", javaType = "String")
    public static final String SIGN_NAME = SMSProperties.SIGN_NAME;

    @Metadata(label = "producer", description = "SMS template code", javaType = "String")
    public static final String TEMPLATE_CODE = SMSProperties.TEMPLATE_CODE;

    @Metadata(label = "producer", description = "SMS template parameters as JSON", javaType = "String")
    public static final String TEMPLATE_PARAM = SMSProperties.TEMPLATE_PARAM;

    @Metadata(label = "producer", description = "Out id for the SMS", javaType = "String")
    public static final String OUT_ID = SMSProperties.OUT_ID;

    @Metadata(label = "producer", description = "Alibaba Cloud request id", javaType = "String")
    public static final String REQUEST_ID = SMSProperties.REQUEST_ID;

    private SMSHeaders() {
    }
}
