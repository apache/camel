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
package org.apache.camel.component.kafka.share;

import org.apache.kafka.clients.consumer.AcknowledgeType;

/**
 * How a record received from a share group is acknowledged.
 */
public enum KafkaShareAcknowledgeType {

    /**
     * The record was processed: it is not delivered again.
     */
    ACCEPT(AcknowledgeType.ACCEPT),
    /**
     * The record was not processed: it is made available again, to this or another consumer of the share group, until
     * the delivery count limit of the broker is reached.
     */
    RELEASE(AcknowledgeType.RELEASE),
    /**
     * The record cannot be processed: it is discarded and not delivered again.
     */
    REJECT(AcknowledgeType.REJECT);

    private final AcknowledgeType acknowledgeType;

    KafkaShareAcknowledgeType(AcknowledgeType acknowledgeType) {
        this.acknowledgeType = acknowledgeType;
    }

    public AcknowledgeType getAcknowledgeType() {
        return acknowledgeType;
    }
}
