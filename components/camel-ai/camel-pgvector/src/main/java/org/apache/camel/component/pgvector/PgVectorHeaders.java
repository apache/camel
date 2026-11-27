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

    @Metadata(description = "The action that the producer performs. It replaces the action option.",
              javaType = "String",
              enums = "CREATE_TABLE,CREATE_INDEX,DROP_TABLE,UPSERT,DELETE,SIMILARITY_SEARCH")
    public static final String ACTION = "CamelPgVectorAction";

    @Metadata(description = "The id of the record, of at most 36 characters. UPSERT inserts the record with this id or"
                            + " replaces it, and uses a random UUID when the header is not set. DELETE requires it.",
              javaType = "String")
    public static final String RECORD_ID = "CamelPgVectorRecordId";

    @Metadata(description = "The maximum number of results to return for similarity search.",
              javaType = "Integer", defaultValue = "3")
    public static final String QUERY_TOP_K = "CamelPgVectorQueryTopK";

    @Metadata(description = "The text that UPSERT stores in the text_content column of the record.",
              javaType = "String")
    public static final String TEXT_CONTENT = "CamelPgVectorTextContent";

    @Metadata(description = "The metadata that UPSERT stores in the metadata column of the record, a TEXT column. The"
                            + " producer does not check the value; the pgvector:embeddings transformer sets it as a JSON"
                            + " object.",
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

    @Metadata(description = "The values of the ? placeholders of the CamelPgVectorFilter header, in order. For example the"
                            + " filter text_content LIKE ? AND metadata::jsonb->>'category' = ? with the List"
                            + " [\"%hello%\", \"science\"]. The producer reads it only when that header is set.",
              javaType = "java.util.List")
    public static final String FILTER_PARAMS = "CamelPgVectorFilterParams";

}
