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
package org.apache.camel.component.aws.sqs;

/**
 * Constants used in Camel AWS SQS module
 */
public interface SqsConstants {

    String ATTRIBUTES = "CamelAwsSqsAttributes";
    String MESSAGE_ATTRIBUTES = "CamelAwsSqsMessageAttributes";
    String MD5_OF_BODY = "CamelAwsSqsMD5OfBody";
    String MESSAGE_ID = "CamelAwsSqsMessageId";
    String RECEIPT_HANDLE = "CamelAwsSqsReceiptHandle";
    String DELAY_HEADER = "CamelAwsSqsDelaySeconds";
    String MESSAGE_GROUP_ID_PROPERTY = "CamelAwsMessageGroupId";
    String SQS_QUEUE_PREFIX = "CamelAwsSqsPrefix";
    String SQS_OPERATION = "CamelAwsSqsOperation";
}
