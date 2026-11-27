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
    @Metadata(description = "Whether UPDATE_DOCUMENT updates several documents. If true, the body"
                            + " must be a Collection of documents that each have their _key, and the CamelArangoDbKey header is"
                            + " not used.",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String MULTI_UPDATE = "CamelArangoDbMultiUpdate";
    @Metadata(description = "Whether SAVE_DOCUMENT saves several documents. If true, the body must be"
                            + " a Collection of documents. A Collection body is saved as several documents even when this"
                            + " header is not set.",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String MULTI_INSERT = "CamelArangoDbMultiInsert";
    @Metadata(description = "Whether DELETE_DOCUMENT deletes several documents. If true, the body must"
                            + " be a Collection of the keys of the documents. If false, the body is the key of one document.",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String MULTI_DELETE = "CamelArangoDbMultiDelete";
    @Metadata(description = "The key, as a String, of the document, vertex or edge that"
                            + " UPDATE_DOCUMENT, UPDATE_VERTEX and UPDATE_EDGE update. The find and delete operations take the"
                            + " key from the body instead.",
              javaType = "java.lang.String")
    public static final String ARANGO_KEY = "CamelArangoDbKey";
    @Metadata(description = "The java.lang.Class that FIND_DOCUMENT_BY_KEY, FIND_VERTEX_BY_KEY,"
                            + " FIND_EDGE_BY_KEY and AQL_QUERY convert each result to. A class name as text is not accepted. If"
                            + " not set, BaseEdgeDocument is used for FIND_EDGE_BY_KEY and BaseDocument for the other"
                            + " operations.",
              javaType = "java.lang.Class",
              defaultValue = "BaseDocument.class or BaseEdgeDocument.class")
    public static final String RESULT_CLASS_TYPE = "CamelArangoDbResultClassType";
    @Metadata(description = "The AQL query that AQL_QUERY runs. If not set, the body is the query.",
              javaType = "java.lang.String")
    public static final String AQL_QUERY = "CamelArangoDbAqlQuery";
    @Metadata(description = "The bind parameters of the AQL query of AQL_QUERY, as a Map from the"
                            + " name of each bind parameter to its value, for example foo for @foo in the query.",
              javaType = "java.util.Map")
    public static final String AQL_QUERY_BIND_PARAMETERS = "CamelArangoDbAqlParameters";
    @Metadata(label = "advanced", description = "The com.arangodb.model.AqlQueryOptions of the AQL query"
                                                + " of AQL_QUERY, such as its batch size. If not set, the query runs with the default options.",
              javaType = "com.arangodb.model.AqlQueryOptions")
    public static final String AQL_QUERY_OPTIONS = "CamelArangoDbAqlOptions";

    private ArangoDbConstants() {
    }
}
