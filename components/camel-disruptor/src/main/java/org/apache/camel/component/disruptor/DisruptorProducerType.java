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
package org.apache.camel.component.disruptor;

import com.lmax.disruptor.dsl.ProducerType;

/**
 * This enumeration re-enumerated the values of the {@link ProducerType} according to the Camel Case convention used
 * in Camel.
 * Multi is the default {@link ProducerType}.
 */
public enum DisruptorProducerType {
    /**
     * Create a RingBuffer with a single event publisher to the Disruptors RingBuffer
     */
    Single(ProducerType.SINGLE),
    /**
     * Create a RingBuffer supporting multiple event publishers to the Disruptors RingBuffer
     */
    Multi(ProducerType.MULTI);
    private final ProducerType producerType;

    DisruptorProducerType(ProducerType producerType) {
        this.producerType = producerType;
    }

    public ProducerType getProducerType() {
        return producerType;
    }
}
