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

package org.apache.camel.component.kafka.consumer.support;

import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;
import org.apache.camel.support.resume.OffsetKeys;
import org.apache.camel.support.resume.Offsets;

public class KafkaResumable implements Resumable {
    private final String partition;
    private final String offset;

    public KafkaResumable(String partition, String offset) {
        this.partition = partition;
        this.offset = offset;
    }

    @Override
    public Offset<String> getLastOffset() {
        return Offsets.of(offset);
    }

    @Override
    public OffsetKey<?> getOffsetKey() {
        return OffsetKeys.unmodifiableOf(partition);
    }
}
