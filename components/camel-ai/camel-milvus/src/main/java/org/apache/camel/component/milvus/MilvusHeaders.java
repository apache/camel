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
package org.apache.camel.component.milvus;

import org.apache.camel.spi.Metadata;

public class MilvusHeaders {
    @Metadata(description = "The action that the producer performs. It wins over the action option. The milvus:embeddings"
                            + " transformer reads it too, and accepts only INSERT and UPSERT.",
              javaType = "String",
              enums = "CREATE_COLLECTION,CREATE_INDEX,UPSERT,INSERT,SEARCH,QUERY,DELETE")
    public static final String ACTION = "CamelMilvusAction";

    @Metadata(description = "The status code of the response of Milvus. The producer sets it only after an action that"
                            + " succeeded, so it is always 0. When Milvus returns another code, the producer fails the exchange.",
              javaType = "String")
    public static final String OPERATION_STATUS = "CamelMilvusOperationStatus";

    @Metadata(description = "The status code of the response of Milvus as an int, the same value as the"
                            + " CamelMilvusOperationStatus header.",
              javaType = "int")
    public static final String OPERATION_STATUS_VALUE = "CamelMilvusOperationStatusValue";

    @Metadata(description = "The name of the field for the text of the TextSegment, in the InsertParam or UpsertParam"
                            + " that the milvus:embeddings transformer builds. The default is text.",
              javaType = "String")
    public static final String TEXT_FIELD_NAME = "CamelMilvusTextFieldName";

    @Metadata(description = "The name of the field for the embedding vector, in the InsertParam or UpsertParam that the"
                            + " milvus:embeddings transformer builds. The default is vector.",
              javaType = "String")
    public static final String VECTOR_FIELD_NAME = "CamelMilvusVectorFieldName";

    @Metadata(description = "The name of the collection, in the InsertParam or UpsertParam that the milvus:embeddings"
                            + " transformer builds. The default is embeddings: the transformer does not use the collection of"
                            + " the endpoint.",
              javaType = "String")
    public static final String COLLECTION_NAME = "CamelMilvusCollectionName";

    @Metadata(description = "The name of the primary key field, in the InsertParam or UpsertParam that the"
                            + " milvus:embeddings transformer builds. The default is id. The transformer adds the key field only"
                            + " when the CamelMilvusKeyValue header is set.",
              javaType = "String")
    public static final String KEY_NAME = "CamelMilvusKeyName";

    @Metadata(description = "The value of the primary key, in the InsertParam or UpsertParam that the milvus:embeddings"
                            + " transformer builds. If not set, the transformer adds no key field, as for a collection whose"
                            + " primary key has autoID enabled.",
              javaType = "String")
    public static final String KEY_VALUE = "CamelMilvusKeyValue";
}
