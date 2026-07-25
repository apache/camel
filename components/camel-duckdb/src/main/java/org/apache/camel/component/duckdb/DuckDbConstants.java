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
package org.apache.camel.component.duckdb;

import org.apache.camel.spi.Metadata;

public final class DuckDbConstants {

    @Metadata(description = "Overrides the operation configured on the endpoint.", javaType = "String")
    public static final String OPERATION = "CamelDuckDbOperation";
    @Metadata(description = "Overrides the database path configured on the endpoint.", javaType = "String")
    public static final String DATABASE_PATH = "CamelDuckDbDatabasePath";
    @Metadata(description = "Overrides the target table configured on the endpoint.", javaType = "String")
    public static final String TABLE = "CamelDuckDbTable";
    @Metadata(description = "Overrides the SQL for query or execute operations.", javaType = "String")
    public static final String QUERY = "CamelDuckDbQuery";
    @Metadata(description = "The number of rows written or affected.", javaType = "long")
    public static final String ROWS_WRITTEN = "CamelDuckDbRowsWritten";
    @Metadata(description = "The number of rows returned by a query.", javaType = "long")
    public static final String ROWS_READ = "CamelDuckDbRowsRead";
    @Metadata(description = "The boolean result of a ping operation.", javaType = "boolean")
    public static final String PING_OK = "CamelDuckDbPingOk";

    private DuckDbConstants() {
    }
}
