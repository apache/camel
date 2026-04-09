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
package org.apache.camel.component.pgvector;

import org.apache.camel.spi.Metadata;

public final class PgVectorHeaders {

    private PgVectorHeaders() {
    }

    @Metadata(description = "The action to be performed.",
              javaType = "String",
              enums = "CREATE_TABLE,CREATE_INDEX,DROP_TABLE,UPSERT,DELETE,SIMILARITY_SEARCH")
    public static final String ACTION = "CamelPgVectorAction";

    @Metadata(description = "The id of the vector record.",
              javaType = "String")
    public static final String RECORD_ID = "CamelPgVectorRecordId";

    @Metadata(description = "The maximum number of results to return for similarity search.",
              javaType = "Integer", defaultValue = "3")
    public static final String QUERY_TOP_K = "CamelPgVectorQueryTopK";

    @Metadata(description = "The text content to store alongside the vector embedding.",
              javaType = "String")
    public static final String TEXT_CONTENT = "CamelPgVectorTextContent";

    @Metadata(description = "The metadata associated with the vector record, stored as JSON.",
              javaType = "String")
    public static final String METADATA = "CamelPgVectorMetadata";

    @Metadata(description = "Filter condition for similarity search."
                            + " Applied as a SQL WHERE clause on the text_content and metadata columns."
                            + " Supports parameterized queries using ? placeholders with values provided"
                            + " via the CamelPgVectorFilterParams header."
                            + " WARNING: When not using parameterized queries, the filter value is appended"
                            + " directly as SQL. Never use untrusted input as the filter value without"
                            + " parameterization, as this could lead to SQL injection.",
              javaType = "String")
    public static final String FILTER = "CamelPgVectorFilter";

    @Metadata(description = "Parameter values for parameterized filter queries."
                            + " Use with ? placeholders in the CamelPgVectorFilter header."
                            + " Example: filter = 'text_content LIKE ? AND metadata::jsonb->>'category' = ?'"
                            + " with filterParams = List.of(\"%hello%\", \"science\").",
              javaType = "java.util.List")
    public static final String FILTER_PARAMS = "CamelPgVectorFilterParams";

}
