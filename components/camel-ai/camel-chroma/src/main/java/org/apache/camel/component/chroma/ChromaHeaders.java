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
package org.apache.camel.component.chroma;

import org.apache.camel.spi.Metadata;

public final class ChromaHeaders {

    @Metadata(description = "The action to be performed.", javaType = "String",
              enums = "CREATE_COLLECTION,DELETE_COLLECTION,GET_COLLECTION,ADD,QUERY,GET,UPDATE,UPSERT,DELETE")
    public static final String ACTION = "CamelChromaAction";

    @Metadata(description = "The collection name.", javaType = "String")
    public static final String COLLECTION_NAME = "CamelChromaCollectionName";

    @Metadata(description = "The embedding IDs.", javaType = "java.util.List<String>")
    public static final String IDS = "CamelChromaIds";

    @Metadata(description = "The embeddings.", javaType = "java.util.List<java.util.List<Float>>")
    public static final String EMBEDDINGS = "CamelChromaEmbeddings";

    @Metadata(description = "The metadata for embeddings.", javaType = "java.util.List<java.util.Map<String, String>>")
    public static final String METADATAS = "CamelChromaMetadatas";

    @Metadata(description = "The documents for embeddings.", javaType = "java.util.List<String>")
    public static final String DOCUMENTS = "CamelChromaDocuments";

    @Metadata(description = "The query embeddings for similarity search.", javaType = "java.util.List<java.util.List<Float>>")
    public static final String QUERY_EMBEDDINGS = "CamelChromaQueryEmbeddings";

    @Metadata(description = "The number of results to return.", javaType = "Integer", defaultValue = "10")
    public static final String N_RESULTS = "CamelChromaNResults";

    @Metadata(description = "Chroma where filter.", javaType = "java.util.Map<String, Object>")
    public static final String WHERE = "CamelChromaWhere";

    @Metadata(description = "Chroma where document filter.", javaType = "java.util.Map<String, Object>")
    public static final String WHERE_DOCUMENT = "CamelChromaWhereDocument";

    @Metadata(description = "The fields to include in the result.", javaType = "java.util.List<String>")
    public static final String INCLUDE = "CamelChromaInclude";

    @Metadata(description = "The operation status.", javaType = "String")
    public static final String OPERATION_STATUS = "CamelChromaOperationStatus";

    private ChromaHeaders() {
    }
}
