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
package org.apache.camel.component.kudu;

import org.apache.camel.spi.Metadata;

public final class KuduConstants {
    @Metadata(description = "The org.apache.kudu.Schema with the columns of the table that the CREATE_TABLE operation"
                            + " creates. Required for CREATE_TABLE.",
              javaType = "org.apache.kudu.Schema")
    public static final String CAMEL_KUDU_SCHEMA = "CamelKuduSchema";
    @Metadata(description = "The options of the table that the CREATE_TABLE operation creates, such as its partitioning"
                            + " and its number of replicas. Required for CREATE_TABLE, and the options must set range or"
                            + " hash partitioning.",
              javaType = "org.apache.kudu.client.CreateTableOptions")
    public static final String CAMEL_KUDU_TABLE_OPTIONS = "CamelKuduTableOptions";
    @Metadata(description = "The names of the columns that the SCAN operation returns for each row. If not set, the scan"
                            + " returns all the columns of the table.",
              javaType = "java.util.List<String>")
    public static final String CAMEL_KUDU_SCAN_COLUMN_NAMES = "CamelKuduScanColumnNames";
    @Metadata(description = "The predicate on a column that the rows of the SCAN operation must match. If not set, the"
                            + " scan returns all the rows of the table.",
              javaType = "org.apache.kudu.client.KuduPredicate")
    public static final String CAMEL_KUDU_SCAN_PREDICATE = "CamelKuduScanPredicate";
    @Metadata(description = "The maximum number of rows that the SCAN operation returns. The value must be a"
                            + " java.lang.Long, as an Integer or a String fails the exchange. If not set, the number of rows"
                            + " is not limited.",
              javaType = "long")
    public static final String CAMEL_KUDU_SCAN_LIMIT = "CamelKuduScanLimit";

    private KuduConstants() {
    }
}
