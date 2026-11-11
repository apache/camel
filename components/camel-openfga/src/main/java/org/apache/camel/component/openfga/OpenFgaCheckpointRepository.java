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
package org.apache.camel.component.openfga;

/**
 * Durable storage for an OpenFGA change-log consumer's opaque cursor. Implementations must durably commit a store
 * before returning, preserve the previous value on failure, and impose finite I/O deadlines. Use one active consumer
 * per checkpoint key; sharing a key between concurrent consumers is unsupported. The application owns the repository
 * and its lifecycle.
 */
public interface OpenFgaCheckpointRepository {
    /** Returns the last committed cursor, or null when the consumer has no checkpoint yet. */
    String load(String checkpointKey) throws Exception;

    /** Atomically and durably replaces the committed cursor. */
    void store(String checkpointKey, String continuationToken) throws Exception;
}
