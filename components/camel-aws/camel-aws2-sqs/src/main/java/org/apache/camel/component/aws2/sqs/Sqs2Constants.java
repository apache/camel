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
package org.apache.camel.component.aws2.sqs;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS SQS module
 */
public interface Sqs2Constants {

    @Metadata(label = "consumer",
              description = "A map of the attributes requested in ReceiveMessage to their respective values.",
              javaType = "Map<MessageSystemAttributeName, String>")
    String ATTRIBUTES = "CamelAwsSqsAttributes";
    @Metadata(label = "consumer", description = "The Amazon SQS message attributes.",
              javaType = "Map<String, MessageAttributeValue>")
    String MESSAGE_ATTRIBUTES = "CamelAwsSqsMessageAttributes";
    @Metadata(description = "The MD5 checksum of the Amazon SQS message.", javaType = "String")
    String MD5_OF_BODY = "CamelAwsSqsMD5OfBody";
    @Metadata(description = "The Amazon SQS message ID.", javaType = "String")
    String MESSAGE_ID = "CamelAwsSqsMessageId";
    @Metadata(description = "The Amazon SQS message receipt handle.", javaType = "String")
    String RECEIPT_HANDLE = "CamelAwsSqsReceiptHandle";
    @Metadata(label = "producer", description = "The delay seconds that the Amazon SQS message can be\n" +
                                                "see by others.",
              javaType = "Integer")
    String DELAY_HEADER = "CamelAwsSqsDelaySeconds";
    String MESSAGE_GROUP_ID_PROPERTY = "CamelAwsMessageGroupId";
    @Metadata(description = "A string to use for filtering the list results.", javaType = "String")
    String SQS_QUEUE_PREFIX = "CamelAwsSqsPrefix";
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String SQS_OPERATION = "CamelAwsSqsOperation";
    String SQS_DELETE_FILTERED = "CamelAwsSqsDeleteFiltered";
}
