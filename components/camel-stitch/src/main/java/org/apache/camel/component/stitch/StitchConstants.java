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
package org.apache.camel.component.stitch;

import org.apache.camel.spi.Metadata;

public final class StitchConstants {
    private static final String HEADER_PREFIX = "CamelStitch";
    // headers evaluated by producer
    @Metadata(label = "producer", description = "The name of the destination table the data is being pushed to. " +
                                                "Table names must be unique in each destination schema, or loading issues will occur. "
                                                +
                                                "Note: The number of characters in the table name should be within the destinations allowed limits or data will rejected.",
              javaType = "String")
    public static final String TABLE_NAME = HEADER_PREFIX + "TableName";
    @Metadata(label = "producer", description = "The schema that describes the Stitch message",
              javaType = "StitchSchema or Map")
    public static final String SCHEMA = HEADER_PREFIX + "Schema";
    @Metadata(label = "producer",
              description = "A collection of strings representing the Primary Key fields in the source table. " +
                            "Stitch use these Primary Keys to de-dupe data during loading If not provided, the table will be loaded in an append-only manner.",
              javaType = "Collection<String>")
    public static final String KEY_NAMES = HEADER_PREFIX + "KeyNames";
    // headers set by producer
    @Metadata(label = "producer", description = "HTTP Status code that is returned from Stitch Import HTTP API.",
              javaType = "Integer")
    public static final String CODE = HEADER_PREFIX + "Code";
    @Metadata(label = "producer", description = "HTTP headers that are returned from Stitch Import HTTP API.",
              javaType = "Map<String, Object>")
    public static final String HEADERS = HEADER_PREFIX + "Headers";
    @Metadata(label = "producer",
              description = "The status message that Stitch returns after sending the data through Stitch Import API.",
              javaType = "String")
    public static final String STATUS = HEADER_PREFIX + "Status";

    private StitchConstants() {
    }
}
