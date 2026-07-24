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
package org.apache.camel.component.clickhouse;

import org.apache.camel.spi.Metadata;

public final class ClickHouseConstants {

    @Metadata(description = "Overrides the operation configured on the endpoint (insert, query or ping).",
              javaType = "String")
    public static final String OPERATION = "CamelClickHouseOperation";
    @Metadata(description = "Overrides the target database configured on the endpoint.", javaType = "String")
    public static final String DATABASE = "CamelClickHouseDatabase";
    @Metadata(description = "Overrides the target table configured on the endpoint.", javaType = "String")
    public static final String TABLE = "CamelClickHouseTable";
    @Metadata(description = "Overrides the data format configured on the endpoint.", javaType = "String")
    public static final String FORMAT = "CamelClickHouseFormat";
    @Metadata(description = "The number of rows written by an insert operation.", javaType = "long")
    public static final String WRITTEN_ROWS = "CamelClickHouseWrittenRows";
    @Metadata(description = "The number of rows read by a query operation.", javaType = "long")
    public static final String READ_ROWS = "CamelClickHouseReadRows";
    @Metadata(description = "The boolean result of a ping operation.", javaType = "boolean")
    public static final String PING_OK = "CamelClickHousePingOk";

    private ClickHouseConstants() {
    }
}
