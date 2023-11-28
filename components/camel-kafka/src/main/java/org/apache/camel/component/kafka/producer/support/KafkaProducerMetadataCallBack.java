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
package org.apache.camel.component.kafka.producer.support;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

import static org.apache.camel.component.kafka.producer.support.ProducerUtil.setException;
import static org.apache.camel.component.kafka.producer.support.ProducerUtil.setRecordMetadata;

public class KafkaProducerMetadataCallBack implements Callback {
    private final Object body;
    private final boolean recordMetadata;

    public KafkaProducerMetadataCallBack(Object body, boolean recordMetadata) {
        this.body = body;
        this.recordMetadata = recordMetadata;
    }

    @Override
    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
        setException(body, e);

        if (this.recordMetadata) {
            setRecordMetadata(body, recordMetadata);
        }
    }
}
