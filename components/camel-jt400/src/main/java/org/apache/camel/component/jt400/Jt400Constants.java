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
package org.apache.camel.component.jt400;

import org.apache.camel.spi.Metadata;

public interface Jt400Constants {

    //header names
    @Metadata(label = "consumer",
              description = "*Data queues:* Returns the sender information for this data queue entry, or an empty string if not available."
                            +
                            "*Message queues: The job identifier of the sending job",
              javaType = "String")
    String SENDER_INFORMATION = "SENDER_INFORMATION";

    // Used only for keyed data queue support
    @Metadata(description = "The data queue key.", javaType = "String or byte[]")
    String KEY = "KEY";

    // Used only for message queue support
    @Metadata(label = "consumer", description = "The message received", javaType = "com.ibm.as400.access.QueuedMessage")
    String MESSAGE = "CamelJt400Message";
    @Metadata(label = "consumer", description = "The message identifier", javaType = "String")
    String MESSAGE_ID = "CamelJt400MessageID";
    @Metadata(label = "consumer", description = "The message file name", javaType = "String")
    String MESSAGE_FILE = "CamelJt400MessageFile";
    @Metadata(label = "consumer", description = "The message type (corresponds to constants defined in the AS400Message class)",
              javaType = "Integer")
    String MESSAGE_TYPE = "CamelJt400MessageType";
    @Metadata(label = "consumer",
              description = "The message severity (Valid values are between 0 and 99, or -1 if it is not set)",
              javaType = "Integer")
    String MESSAGE_SEVERITY = "CamelJt400MessageSeverity";
    @Metadata(label = "consumer", description = "The default message reply, when the message is an inquiry message",
              javaType = "String")
    String MESSAGE_DFT_RPY = "CamelJt400MessageDefaultReply";
    @Metadata(description = "*Consumer:* The key of the message that will be replied to (if the `sendingReply` parameter is set to `true`). "
                            +
                            "*Producer:* If set, and if the message body is not empty, a new message will not be sent to the provided message queue. "
                            +
                            "Instead, a response will be sent to the message identified by the given key. " +
                            "This is set automatically when reading from the message queue if the `sendingReply` parameter is set to `true`.",
              javaType = "byte[]")
    String MESSAGE_REPLYTO_KEY = "CamelJt400MessageReplyToKey";

}
