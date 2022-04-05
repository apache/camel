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
package org.apache.camel.component.sjms;

import org.apache.camel.component.sjms.jms.JmsConstants;
import org.apache.camel.spi.Metadata;

public interface SjmsConstants {

    String JMS_MESSAGE_TYPE = "CamelJmsMessageType";
    String JMS_SESSION = "CamelJMSSession";
    @Metadata(label = "producer", description = "DestinationName is a JMS queue or topic name. " +
                                                "By default, the destinationName is interpreted as a queue name.",
              javaType = "String")
    String JMS_DESTINATION_NAME = "CamelJMSDestinationName";
    @Metadata(label = "producer",
              description = "The timeout for waiting for a reply when using the InOut Exchange Pattern (in milliseconds).",
              javaType = "long")
    String JMS_REQUEST_TIMEOUT = "CamelJmsRequestTimeout";
    String JMS_DELIVERY_MODE = "CamelJmsDeliveryMode";
    @Metadata(label = "producer", description = "The correlation ID.", javaType = "String")
    String JMS_CORRELATION_ID = JmsConstants.JMS_CORRELATION_ID;
    @Metadata(label = "producer",
              description = "Provides an explicit ReplyTo destination (overrides any incoming value of Message.getJMSReplyTo() in consumer)",
              javaType = "String")
    String JMS_REPLY_TO = JmsConstants.JMS_REPLY_TO;

}
