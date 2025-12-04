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
    @Metadata(
            description = "The action to be performed.",
            javaType = "String",
            enums = "CREATE_COLLECTION,CREATE_INDEX,UPSERT,INSERT,SEARCH,QUERY,DELETE")
    public static final String ACTION = "CamelMilvusAction";

    @Metadata(description = "Operation Status.", javaType = "String")
    public static final String OPERATION_STATUS = "CamelMilvusOperationStatus";

    @Metadata(description = "Operation Status Value.", javaType = "int")
    public static final String OPERATION_STATUS_VALUE = "CamelMilvusOperationStatusValue";

    @Metadata(description = "Text Field Name for Insert/Upsert operation", javaType = "String")
    public static final String TEXT_FIELD_NAME = "CamelMilvusTextFieldName";

    @Metadata(description = "Vector Field Name for Insert/Upsert operation", javaType = "String")
    public static final String VECTOR_FIELD_NAME = "CamelMilvusVectorFieldName";

    @Metadata(description = "Collection Name for Insert/Upsert operation", javaType = "String")
    public static final String COLLECTION_NAME = "CamelMilvusCollectionName";

    @Metadata(description = "Key Name for Insert/Upsert operation", javaType = "String")
    public static final String KEY_NAME = "CamelMilvusKeyName";

    @Metadata(description = "Key Value for Insert/Upsert operation", javaType = "String")
    public static final String KEY_VALUE = "CamelMilvusKeyValue";
}
