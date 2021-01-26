package org.apache.camel.component.huaweicloud.smn.constants;

public class SmnProperties {

    public SmnProperties() {
    }

    // request properties
    public static String TEMPLATE_NAME = "CamelHwCloudSmnTemplateName";
    public static String TEMPLATE_TAGS = "CamelHwCloudSmnTemplateTags";
    public static String SMN_OPERATION = "CamelHwCloudSmnOperation";
    public static String NOTIFICATION_TOPIC_NAME = "CamelHwCloudSmnTopic";
    public static String NOTIFICATION_SUBJECT = "CamelHwCloudSmnSubject";
    public static String NOTIFICATION_TTL = "CamelHwCloudSmnMessageTtl";

    //response properties
    public static String SERVICE_MESSAGE_ID = "CamelHwCloudSmnMesssageId";
    public static String SERVICE_REQUEST_ID = "CamelHwCloudSmnRequestId";
}
