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
package org.apache.camel.component.mtconnect;

/**
 * The Header element of an agent document. Attributes the document does not carry are null: a probe has no sequence
 * numbers, and an error document may have no instance id.
 */
record DocumentHeader(
        Long instanceId, Long bufferSize, Long assetBufferSize, Long firstSequence, Long nextSequence, Long lastSequence) {

    /** Whether the agent has nothing beyond this document: its next sequence follows the agent's last one. */
    boolean drained() {
        return nextSequence != null && lastSequence != null && nextSequence > lastSequence;
    }
}
