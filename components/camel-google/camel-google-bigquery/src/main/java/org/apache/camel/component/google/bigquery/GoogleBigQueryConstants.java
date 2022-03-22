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
package org.apache.camel.component.google.bigquery;

import org.apache.camel.spi.Metadata;

public final class GoogleBigQueryConstants {

    // All the schemes
    public static final String SCHEME_BIGQUERY_SQL = "google-bigquery-sql";
    public static final String SCHEME_BIGQUERY = "google-bigquery";

    @Metadata(description = "Table suffix to use when inserting data", javaType = "String", applicableFor = SCHEME_BIGQUERY)
    public static final String TABLE_SUFFIX = "CamelGoogleBigQueryTableSuffix";
    @Metadata(description = "Table id where data will be submitted. If specified will override endpoint configuration",
              javaType = "String", applicableFor = SCHEME_BIGQUERY)
    public static final String TABLE_ID = "CamelGoogleBigQueryTableId";
    @Metadata(description = "InsertId to use when inserting data", javaType = "String", applicableFor = SCHEME_BIGQUERY)
    public static final String INSERT_ID = "CamelGoogleBigQueryInsertId";
    @Metadata(description = "Partition decorator to indicate partition to use when inserting data", javaType = "String",
              applicableFor = SCHEME_BIGQUERY)
    public static final String PARTITION_DECORATOR = "CamelGoogleBigQueryPartitionDecorator";
    @Metadata(description = "Preprocessed query text", javaType = "String", applicableFor = SCHEME_BIGQUERY_SQL)
    public static final String TRANSLATED_QUERY = "CamelGoogleBigQueryTranslatedQuery";
    @Metadata(description = "A custom `JobId` to use", javaType = "com.google.cloud.bigquery.JobId",
              applicableFor = SCHEME_BIGQUERY_SQL)
    public static final String JOB_ID = "CamelGoogleBigQueryJobId";

    /**
     * Prevent instantiation.
     */
    private GoogleBigQueryConstants() {
    }
}
