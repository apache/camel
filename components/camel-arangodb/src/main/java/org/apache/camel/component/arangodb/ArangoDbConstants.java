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
package org.apache.camel.component.arangodb;

import org.apache.camel.spi.Metadata;

public final class ArangoDbConstants {
    @Metadata(description = "Indicates if there are multiple documents to update. If set to `true`, the body of the message must be a `Collection` of documents to update.",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String MULTI_UPDATE = "CamelArangoDbMultiUpdate";
    @Metadata(description = "Indicates if there are multiple documents to insert. If set to `true`, the body of the message must be a `Collection` of documents to insert.",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String MULTI_INSERT = "CamelArangoDbMultiInsert";
    @Metadata(description = "Indicates if there are multiple documents to delete. If set to `true`, the body of the message must be a `Collection` of key of documents to delete.",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String MULTI_DELETE = "CamelArangoDbMultiDelete";
    @Metadata(description = "The Arango key to use for the operation.", javaType = "java.lang.String")
    public static final String ARANGO_KEY = "key";
    @Metadata(description = "The type of the result of the operation.", javaType = "java.lang.Class",
              defaultValue = "BaseDocument.class or BaseEdgeDocument.class")
    public static final String RESULT_CLASS_TYPE = "ResultClassType";
    @Metadata(description = "The AQL query to execute.", javaType = "java.lang.String")
    public static final String AQL_QUERY = "CamelArangoDbAqlQuery";
    @Metadata(description = "The key/value pairs defining the variables to bind the query to.",
              javaType = "java.util.Map")
    public static final String AQL_QUERY_BIND_PARAMETERS = "CamelArangoDbAqlParameters";
    @Metadata(label = "advanced", description = "The additional options that will be passed to the query API.",
              javaType = "com.arangodb.model.AqlQueryOptions")
    public static final String AQL_QUERY_OPTIONS = "CamelArangoDbAqlOptions";

    private ArangoDbConstants() {
    }
}
