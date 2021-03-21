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
package org.apache.camel.component.kafka;

/**
 * DISCARD will discard the message and continue to poll next message. ERROR_HANDLER will use Camel's error handler to
 * process the exception, and afterwards continue to poll next message. RECONNECT will re-connect the consumer and try
 * poll the message again RETRY will let the consumer retry polling the same message again STOP will stop the consumer
 * (have to be manually started/restarted if the consumer should be able to consume messages again)
 */
public enum PollOnError {
    DISCARD,
    ERROR_HANDLER,
    RECONNECT,
    RETRY,
    STOP
}
