package org.apache.camel.component.huaweicloud.smn.constants;

public class SmnProperties {

    public SmnProperties() {
    }

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
}
