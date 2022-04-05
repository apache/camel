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
package org.apache.camel.component.nats;

import org.apache.camel.spi.Metadata;

public interface NatsConstants {

    @Metadata(description = "The timestamp of a consumed message.", javaType = "long")
    String NATS_MESSAGE_TIMESTAMP = "CamelNatsMessageTimestamp";
    @Metadata(description = "The SID of a consumed message.", javaType = "String")
    String NATS_SID = "CamelNatsSID";
    @Metadata(description = "The ReplyTo of a consumed message (may be null).", javaType = "String")
    String NATS_REPLY_TO = "CamelNatsReplyTo";
    @Metadata(description = "The Subject of a consumed message.", javaType = "String")
    String NATS_SUBJECT = "CamelNatsSubject";
    @Metadata(description = "The Queue name of a consumed message (may be null).", javaType = "String")
    String NATS_QUEUE_NAME = "CamelNatsQueueName";
    String NATS_REQUEST_TIMEOUT_THREAD_PROFILE_NAME = "CamelNatsRequestTimeoutExecutor";
}
