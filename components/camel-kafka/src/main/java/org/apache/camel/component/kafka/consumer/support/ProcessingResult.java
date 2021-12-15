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

public final class ProcessingResult {
    private static final ProcessingResult UNPROCESSED_RESULT = new ProcessingResult(false, KafkaRecordProcessor.START_OFFSET);

    private final boolean breakOnErrorHit;
    private final long partitionLastOffset;

    ProcessingResult(boolean breakOnErrorHit, long partitionLastOffset) {
        this.breakOnErrorHit = breakOnErrorHit;
        this.partitionLastOffset = partitionLastOffset;
    }

    public boolean isBreakOnErrorHit() {
        return breakOnErrorHit;
    }

    public long getPartitionLastOffset() {
        return partitionLastOffset;
    }

    public static ProcessingResult newUnprocessed() {
        return UNPROCESSED_RESULT;
    }
}
