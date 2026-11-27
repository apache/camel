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

    @Metadata(description = "The suffix of a template table: BigQuery inserts the rows into the table whose ID is the table ID"
                            + " followed by this suffix, and creates it with the schema of the table ID table if it does not"
                            + " exist.",
              javaType = "String", applicableFor = SCHEME_BIGQUERY)
    public static final String TABLE_SUFFIX = "CamelGoogleBigQueryTableSuffix";
    @Metadata(description = "The ID of the table to insert the rows into. It replaces the tableId of the endpoint. In a grouped"
                            + " exchange, an exchange without this header goes to the table of the exchange before it.",
              javaType = "String", applicableFor = SCHEME_BIGQUERY)
    public static final String TABLE_ID = "CamelGoogleBigQueryTableId";
    @Metadata(description = "The insert ID of the row of a Map body, which BigQuery uses to drop duplicate rows on a best-effort"
                            + " basis. It is not used for a List body, nor when the useAsInsertId option is set.",
              javaType = "String", applicableFor = SCHEME_BIGQUERY)
    public static final String INSERT_ID = "CamelGoogleBigQueryInsertId";
    @Metadata(description = "The partition of a partitioned table to insert the rows into, such as 20260131 for a day. The"
                            + " producer appends it to the table ID as table$partition.",
              javaType = "String", applicableFor = SCHEME_BIGQUERY)
    public static final String PARTITION_DECORATOR = "CamelGoogleBigQueryPartitionDecorator";
    @Metadata(description = "The query that the producer sends to BigQuery, after it has replaced the ${name} placeholders."
                            + " The producer sets it.",
              javaType = "String", applicableFor = SCHEME_BIGQUERY_SQL)
    public static final String TRANSLATED_QUERY = "CamelGoogleBigQueryTranslatedQuery";
    @Metadata(description = "The ID of a query job. When set, the producer does not run the query but waits for this job and"
                            + " returns its result, for example to read the next page with the CamelGoogleBigQueryPageToken"
                            + " header. The producer sets it after a SELECT with the SELECT_LIST output type.",
              javaType = "com.google.cloud.bigquery.JobId",
              applicableFor = SCHEME_BIGQUERY_SQL)
    public static final String JOB_ID = "CamelGoogleBigQueryJobId";
    @Metadata(description = "The token of the page of SELECT results to return, from the CamelGoogleBigQueryNextPageToken"
                            + " header of a previous call. It replaces the pageToken option. Send it with the"
                            + " CamelGoogleBigQueryJobId header of that call, so the page is read from the same job.",
              javaType = "String", applicableFor = SCHEME_BIGQUERY_SQL)
    public static final String PAGE_TOKEN = "CamelGoogleBigQueryPageToken";
    @Metadata(description = "The token of the next page of SELECT results, or null for the last page. The producer sets it"
                            + " with the SELECT_LIST output type. Send it in the CamelGoogleBigQueryPageToken header to read the"
                            + " next page.",
              javaType = "String", applicableFor = SCHEME_BIGQUERY_SQL)
    public static final String NEXT_PAGE_TOKEN = "CamelGoogleBigQueryNextPageToken";

    /**
     * Prevent instantiation.
     */
    private GoogleBigQueryConstants() {
    }
}
