/**
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
package org.apache.camel.component.sjms.jms;

/**
 * JMS constants
 */
public interface JmsConstants {

    String QUEUE_PREFIX = "queue:";
    String TOPIC_PREFIX = "topic:";
    String TEMP_QUEUE_PREFIX = "temp:queue:";
    String TEMP_TOPIC_PREFIX = "temp:topic:";

    /**
     * Set by the publishing Client
     */
    String JMS_CORRELATION_ID = "JMSCorrelationID";

    /**
     * Set on the send or publish event
     */
    String JMS_DELIVERY_MODE = "JMSDeliveryMode";

    /**
     * Set on the send or publish event
     */
    String JMS_DESTINATION = "JMSDestination";

    /**
     * Set on the send or publish event
     */
    String JMS_EXPIRATION = "JMSExpiration";

    /**
     * Set on the send or publish event
     */
    String JMS_MESSAGE_ID = "JMSMessageID";

    /**
     * Set on the send or publish event
     */
    String JMS_PRIORITY = "JMSPriority";

    /**
     * A redelivery flag set by the JMS provider
     */
    String JMS_REDELIVERED = "JMSRedelivered";

    /**
     * The JMS Reply To {@link javax.jms.Destination} set by the publishing Client
     */
    String JMS_REPLY_TO = "JMSReplyTo";

    /**
     * Set on the send or publish event
     */
    String JMS_TIMESTAMP = "JMSTimestamp";

    /**
     * Set by the publishing Client
     */
    String JMS_TYPE = "JMSType";

    /**
     * Custom headers
     */
    String JMSX_GROUP_ID = "JMSXGroupID";

    /**
     * String representation of JMS delivery modes.
     */
    String JMS_DELIVERY_MODE_PERSISTENT = "PERSISTENT";

    String JMS_DELIVERY_MODE_NON_PERSISTENT = "NON_PERSISTENT";

}