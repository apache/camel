
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
package org.apache.camel.component.weaviate;

import org.apache.camel.spi.Metadata;

public class WeaviateVectorDbHeaders {
    @Metadata(description = "The action to be performed.", javaType = "String",
              enums = "CREATE_COLLECTION,CREATE,DELETE_BY_ID,DELETE_COLLECTION,QUERY,QUERY_BY_ID,UPDATE_BY_ID")
    public static final String ACTION = "CamelWeaviateAction";

    @Metadata(description = "Text Field Name for Create/Update/Query operation", javaType = "String")
    public static final String TEXT_FIELD_NAME = "CamelWeaviateTextFieldName";

    @Metadata(description = "Vector Field Name for Create/Update/Query operation", javaType = "String")
    public static final String VECTOR_FIELD_NAME = "CamelweaviateVectorFieldName";

    @Metadata(description = "Collection Name for all operations", javaType = "String")
    public static final String COLLECTION_NAME = "CamelWeaviateCollectionName";

    @Metadata(description = "Weaviate Object fields", javaType = "HashMap")
    public static final String FIELDS = "CamelWeaviateFields";

    @Metadata(description = "Weaviate Object properties", javaType = "HashMap")
    public static final String PROPERTIES = "CamelWeaviateProperties";

    @Metadata(description = "Index Id", javaType = "String")
    public static final String INDEX_ID = "CamelWeaviateIndexId";

    @Metadata(description = "Query Top K", javaType = "Integer")
    public static final String QUERY_TOP_K = "CamelWeaviateQueryTopK";

    @Metadata(description = "Merges properties into the object", javaType = "Boolean", defaultValue = "true")
    public static final String UPDATE_WITH_MERGE = "CamelWeaviateUpdateWithMerge";

    @Metadata(description = "Key Name for Create/Update/Query operation", javaType = "String")
    public static final String KEY_NAME = "CamelWeaviateKeyName";

    @Metadata(description = "Key Value for Create/Update/Query operation", javaType = "String")
    public static final String KEY_VALUE = "CamelWeaviateKeyValue";

}
