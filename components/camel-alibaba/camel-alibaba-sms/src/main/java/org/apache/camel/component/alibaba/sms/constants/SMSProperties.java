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

public final class SMSProperties {

    @Metadata(label = "producer", description = "Operation to perform", javaType = "String")
    public static final String OPERATION = "CamelAlibabaSmsOperation";

    @Metadata(label = "producer", description = "Phone numbers to send SMS to", javaType = "String")
    public static final String PHONE_NUMBERS = "CamelAlibabaSmsPhoneNumbers";

    @Metadata(label = "producer", description = "SMS sign name", javaType = "String")
    public static final String SIGN_NAME = "CamelAlibabaSmsSignName";

    @Metadata(label = "producer", description = "SMS template code", javaType = "String")
    public static final String TEMPLATE_CODE = "CamelAlibabaSmsTemplateCode";

    @Metadata(label = "producer", description = "SMS template parameters as JSON", javaType = "String")
    public static final String TEMPLATE_PARAM = "CamelAlibabaSmsTemplateParam";

    @Metadata(label = "producer", description = "Out id for the SMS", javaType = "String")
    public static final String OUT_ID = "CamelAlibabaSmsOutId";

    @Metadata(label = "producer", description = "Request id returned by SMS API", javaType = "String")
    public static final String REQUEST_ID = "CamelAlibabaSmsRequestId";

    private SMSProperties() {
    }
}
