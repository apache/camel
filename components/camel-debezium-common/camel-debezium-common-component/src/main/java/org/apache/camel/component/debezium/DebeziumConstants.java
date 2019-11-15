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
package org.apache.camel.component.debezium;

import io.debezium.relational.history.FileDatabaseHistory;
import org.apache.kafka.connect.storage.FileOffsetBackingStore;

public final class DebeziumConstants {
    // embedded engine constant
    public static final String DEFAULT_OFFSET_STORAGE = FileOffsetBackingStore.class.getName();

    // mysql constant
    public static final String DEFAULT_DATABASE_HISTORY = FileDatabaseHistory.class.getName();

    // header names
    private static final String HEADER_PREFIX = "CamelDebezium";
    public static final String HEADER_SOURCE_METADATA = HEADER_PREFIX + "SourceMetadata";
    public static final String HEADER_IDENTIFIER = HEADER_PREFIX + "Identifier";
    public static final String HEADER_KEY = HEADER_PREFIX + "Key";
    public static final String HEADER_OPERATION = HEADER_PREFIX + "Operation";
    public static final String HEADER_TIMESTAMP = HEADER_PREFIX + "Timestamp";
    public static final String HEADER_BEFORE = HEADER_PREFIX + "Before";

    private DebeziumConstants() {
    }
}
