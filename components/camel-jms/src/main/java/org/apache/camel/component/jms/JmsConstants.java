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
package org.apache.camel.component.jms;

import org.apache.camel.spi.Metadata;

/**
 * JMS constants
 */
public final class JmsConstants {

    @Metadata(label = "producer", description = "The destination.", javaType = "jakarta.jms.Destination")
    public static final String JMS_DESTINATION = "CamelJmsDestination";
    @Metadata(label = "producer", description = "The name of the queue or topic to use as destination.", javaType = "String")
    public static final String JMS_DESTINATION_NAME = "CamelJmsDestinationName";
    @Metadata(description = "The name of the queue or topic the message was sent to.", javaType = "String")
    public static final String JMS_DESTINATION_NAME_PRODUCED = "CamelJMSDestinationProduced";
    @Metadata(description = "The JMS group ID.", javaType = "String")
    public static final String JMS_X_GROUP_ID = "JMSXGroupID";
    @Metadata(description = "The JMS unique message ID.", javaType = "String")
    public static final String JMS_HEADER_MESSAGE_ID = "JMSMessageID";
    @Metadata(description = "The JMS correlation ID.", javaType = "String")
    public static final String JMS_HEADER_CORRELATION_ID = "JMSCorrelationID";
    @Metadata(description = "The JMS correlation ID as bytes.", javaType = "byte[]")
    public static final String JMS_HEADER_CORRELATION_ID_AS_BYTES = "JMSCorrelationIDAsBytes";
    @Metadata(description = "The JMS delivery mode.", javaType = "int")
    public static final String JMS_HEADER_DELIVERY_MODE = "JMSDeliveryMode";
    @Metadata(description = "The JMS destination.", javaType = "jakarta.jms.Destination")
    public static final String JMS_HEADER_DESTINATION = "JMSDestination";
    @Metadata(description = "The JMS expiration.", javaType = "long")
    public static final String JMS_HEADER_EXPIRATION = "JMSExpiration";
    @Metadata(description = "The JMS priority (with 0 as the lowest priority\n" +
                            "and 9 as the highest).",
              javaType = "int")
    public static final String JMS_HEADER_PRIORITY = "JMSPriority";
    @Metadata(description = "Is the JMS message redelivered.", javaType = "boolean")
    public static final String JMS_HEADER_REDELIVERED = "JMSRedelivered";
    @Metadata(description = "The JMS timestamp.", javaType = "long")
    public static final String JMS_HEADER_TIMESTAMP = "JMSTimestamp";
    @Metadata(description = "The JMS reply-to destination.", javaType = "jakarta.jms.Destination")
    public static final String JMS_HEADER_REPLY_TO = "JMSReplyTo";
    @Metadata(description = "The JMS type.", javaType = "String")
    public static final String JMS_HEADER_TYPE = "JMSType";
    @Metadata(description = "The XUser id.", javaType = "String")
    public static final String JMS_HEADER_XUSER_ID = "JMSXUserID";
    @Metadata(description = "The message type.", javaType = "org.apache.camel.component.jms.JmsMessageType")
    public static final String JMS_MESSAGE_TYPE = "CamelJmsMessageType";
    public static final String JMS_DELIVERY_MODE = "CamelJmsDeliveryMode";
    @Metadata(label = "producer",
              description = "The timeout for waiting for a reply when using the InOut Exchange Pattern (in milliseconds).",
              javaType = "long", defaultValue = "20_000")
    public static final String JMS_REQUEST_TIMEOUT = "CamelJmsRequestTimeout";

    private JmsConstants() {
        // utility class
    }

}
