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
package org.apache.camel.component.huaweicloud.smn.constants;

public final class SmnProperties {

    // request properties
    public static final String TEMPLATE_NAME = "CamelHwCloudSmnTemplateName";
    public static final String TEMPLATE_TAGS = "CamelHwCloudSmnTemplateTags";
    public static final String SMN_OPERATION = "CamelHwCloudSmnOperation";
    public static final String NOTIFICATION_TOPIC_NAME = "CamelHwCloudSmnTopic";
    public static final String NOTIFICATION_SUBJECT = "CamelHwCloudSmnSubject";
    public static final String NOTIFICATION_TTL = "CamelHwCloudSmnMessageTtl";

    //response properties
    public static final String SERVICE_MESSAGE_ID = "CamelHwCloudSmnMesssageId";
    public static final String SERVICE_REQUEST_ID = "CamelHwCloudSmnRequestId";

    private SmnProperties() {
    }

}
