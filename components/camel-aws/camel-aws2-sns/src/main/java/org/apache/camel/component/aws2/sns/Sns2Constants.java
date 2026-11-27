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
package org.apache.camel.component.aws2.sns;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS SNS module
 */
public interface Sns2Constants {

    /**
     * AWS SNS has a maximum subject length of 100 characters. Subjects longer than this will be truncated.
     */
    int MAX_SUBJECT_LENGTH = 100;

    @Metadata(description = "The ID that Amazon SNS assigned to the published message. The producer sets it after a"
                            + " publish, but not when batchEnabled is true.",
              javaType = "String")
    String MESSAGE_ID = "CamelAwsSnsMessageId";
    @Metadata(description = "The subject of the message, which Amazon SNS uses as the Subject line of the email for email"
                            + " subscriptions. It replaces the subject option. The producer cuts a subject longer than 100"
                            + " characters, and does not use it when batchEnabled is true.",
              javaType = "String")
    String SUBJECT = "CamelAwsSnsSubject";
    @Metadata(description = "Set to json to send a different message for each protocol, with the body a JSON object with a"
                            + " default key and a key per protocol. It replaces the messageStructure option, and is not used"
                            + " when batchEnabled is true.",
              javaType = "String")
    String MESSAGE_STRUCTURE = "CamelAwsSnsMessageStructure";
    String MESSAGE_GROUP_ID_PROPERTY = "CamelAwsSnsMessageGroupId";

    // Response metadata for FIFO topics
    @Metadata(label = "producer",
              description = "The sequence number that Amazon SNS assigned to the message in a FIFO topic. The producer sets"
                            + " it after a publish, with no value for a standard topic, and not when batchEnabled is true.",
              javaType = "String")
    String SEQUENCE_NUMBER = "CamelAwsSnsSequenceNumber";

    // Batch operation response metadata
    @Metadata(label = "producer",
              description = "The number of entries of the batch that Amazon SNS did not publish, which the producer sets"
                            + " when batchEnabled is true. The PublishBatchResponse in the body lists them with the reason.",
              javaType = "Integer")
    String FAILED_MESSAGE_COUNT = "CamelAwsSnsFailedMessageCount";
    @Metadata(label = "producer",
              description = "The number of entries of the batch that Amazon SNS published, which the producer sets when"
                            + " batchEnabled is true. The PublishBatchResponse in the body lists them with their message ID.",
              javaType = "Integer")
    String SUCCESSFUL_MESSAGE_COUNT = "CamelAwsSnsSuccessfulMessageCount";
}
