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

import org.apache.camel.spi.Metadata;
import org.apache.kafka.connect.storage.FileOffsetBackingStore;

public final class DebeziumConstants {
    // embedded engine constant
    public static final String DEFAULT_OFFSET_STORAGE = FileOffsetBackingStore.class.getName();

    // header names
    private static final String HEADER_PREFIX = "CamelDebezium";
    @Metadata(description = "The source block of the change event as a Map: the connector name, the database, schema and"
                            + " table names, the position in the database log, and so on. Its fields depend on the connector,"
                            + " see the Debezium documentation of the connector.",
              javaType = "Map<String, Object>")
    public static final String HEADER_SOURCE_METADATA = HEADER_PREFIX + "SourceMetadata";
    @Metadata(description = "The Kafka topic name that Debezium gives the event: for a row change the topic prefix, the"
                            + " database or schema name and the table name, for example dbserver1.inventory.customers.",
              javaType = "String")
    public static final String HEADER_IDENTIFIER = HEADER_PREFIX + "Identifier";
    @Metadata(description = "The key of the event as a Kafka Connect Struct, usually the primary key columns of the row."
                            + " Null for a table without a primary key.",
              javaType = "Struct")
    public static final String HEADER_KEY = HEADER_PREFIX + "Key";
    @Metadata(description = "The operation of the change event: c for create (insert), u for update, d for delete, r for a"
                            + " read of a snapshot, and t (truncate) or m (message) on the connectors that send them."
                            + " Not set for an event without an operation, such as a schema change event.",
              javaType = "String")
    public static final String HEADER_OPERATION = HEADER_PREFIX + "Operation";
    @Metadata(description = "The time in milliseconds since the epoch at which the connector processed the event, from the"
                            + " system clock of its JVM (the ts_ms field of the event). Also set as the CamelMessageTimestamp"
                            + " header.",
              javaType = "Long")
    public static final String HEADER_TIMESTAMP = HEADER_PREFIX + "Timestamp";
    @Metadata(description = "The row before the change as a Kafka Connect Struct, for an update or a delete when the"
                            + " connector provides it. Null for an insert and a snapshot read.",
              javaType = "Struct")
    public static final String HEADER_BEFORE = HEADER_PREFIX + "Before";
    @Metadata(description = "The DDL statement of a schema change event. Not set for a row change.", javaType = "String")
    public static final String HEADER_DDL_SQL = HEADER_PREFIX + "DdlSQL";
    @Metadata(description = "If presents, the description of an update: `updatedFields` (the changed fields as a JSON String), "
                            + "`removedFields` (the names of the removed fields) and `truncatedArrays` (the arrays that were "
                            + "truncated, with their `field` and new `size`).",
              javaType = "Map<String, Object>", applicableFor = "debezium-mongodb")
    public static final String HEADER_UPDATE_DESCRIPTION = HEADER_PREFIX + "UpdateDescription";

    private DebeziumConstants() {
    }
}
