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
package org.apache.camel.component.neo4j;

import org.apache.camel.spi.Metadata;

public class Neo4j {

    public static final String SCHEME = "neo4j";

    private Neo4j() {
    }

    public static class Headers {
        //TODO update me
        @Metadata(description = "The operation to be performed.", javaType = "String",
                  enums = "CREATE_NODE,DELETE_NODE,RETRIEVE_NODES,RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY,ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY,CREATE_VECTOR_INDEX,DROP_VECTOR_INDEX,CREATE_VECTOR,VECTOR_SIMILARITY_SEARCH")
        public static final String OPERATION = "CamelNeo4jAction";

        @Metadata(description = "MATCH properties for the generated MATCH query. Needed only if we are matching properties and values. Example: {name: 'Alice'} ",
                  javaType = "String")
        public static final String MATCH_PROPERTIES = "CamelNeo4jMatchProperties";

        @Metadata(description = "Query excetuded", javaType = "String")
        public static final String QUERY_RESULT = "CamelNeo4jQueryResult";

        @Metadata(description = "Query Number of nodes created", javaType = "Long")
        public static final String QUERY_RESULT_NODES_CREATED = "CamelNeo4jQueryResultNodesCreated";
        @Metadata(description = "Query Number of nodes deleted", javaType = "Long")
        public static final String QUERY_RESULT_NODES_DELETED = "CamelNeo4jQueryResultNodesDeleted";
        @Metadata(description = "Query executed contains update", javaType = "Booleab")
        public static final String QUERY_RESULT_CONTAINS_UPDATES = "CamelNeo4jQueryResultContainsUpdates";
        @Metadata(description = "Query executed number of relationships created", javaType = "Long")
        public static final String QUERY_RESULT_RELATIONSHIPS_CREATED = "CamelNeo4jQueryResultRelationshipsCreated";
        @Metadata(description = "Query executed number of relationships deleted", javaType = "Long")
        public static final String QUERY_RESULT_RELATIONSHIPS_DELETED = "CamelNeo4jQueryResultRelationshipsDeleted";

        @Metadata(description = "Number of nodes retrieved", javaType = "Long")
        public static final String QUERY_RETRIEVE_SIZE = "CamelNeo4jQueryResultRetrieveSize";

        @Metadata(description = "Query execution time in Milliseconfs", javaType = "Long")
        public static final String QUERY_RETRIEVE_LIST_NEO4J_NODES = "CamelNeo4jQueryResultListNeo4jNodes";

        @Metadata(description = "Vector Id for the embedding", javaType = "String")
        public static final String VECTOR_ID = "CamelNeo4jVectorEmbeddingId";

        @Metadata(description = "Label for the Node -  used when inserting from Embeddings", javaType = "String")
        public static final String LABEL = "CamelNeo4jLabel";

    }
}
