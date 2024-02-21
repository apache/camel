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
    @Metadata(description = "The metadata about the source event, for example `table` name, database `name`, log position, etc, "
                            +
                            "please refer to the Debezium documentation for more info.",
              javaType = "Map<String, Object>")
    public static final String HEADER_SOURCE_METADATA = HEADER_PREFIX + "SourceMetadata";
    @Metadata(description = "The identifier of the connector, normally is this format \"+++{server-name}.{database-name}.{table-name}+++\".",
              javaType = "String")
    public static final String HEADER_IDENTIFIER = HEADER_PREFIX + "Identifier";
    @Metadata(description = "The key of the event, normally is the table Primary Key.", javaType = "Struct")
    public static final String HEADER_KEY = HEADER_PREFIX + "Key";
    @Metadata(description = "If presents, the type of event operation. Values for the connector are `c` for create (or insert), "
                            +
                            "`u` for update, `d` for delete or `r` for read (in the case of a initial sync) or in case of a snapshot event.",
              javaType = "String")
    public static final String HEADER_OPERATION = HEADER_PREFIX + "Operation";
    @Metadata(description = "If presents, the time (using the system clock in the JVM) at which the connector processed the event.",
              javaType = "Long")
    public static final String HEADER_TIMESTAMP = HEADER_PREFIX + "Timestamp";
    @Metadata(description = "If presents, contains the state of the row before the event occurred.", javaType = "Struct")
    public static final String HEADER_BEFORE = HEADER_PREFIX + "Before";
    @Metadata(description = "If presents, the ddl sql text of the event.", javaType = "String")
    public static final String HEADER_DDL_SQL = HEADER_PREFIX + "DdlSQL";

    private DebeziumConstants() {
    }
}
