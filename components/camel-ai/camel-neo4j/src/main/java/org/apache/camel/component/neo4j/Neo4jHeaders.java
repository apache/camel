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

public class Neo4jHeaders {
    @Metadata(description = "The operation to be performed.", javaType = "String",
              enums = "CREATE_NODE,DELETE_NODE,RETRIEVE_NODES,RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY,ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY,CREATE_VECTOR_INDEX,DROP_VECTOR_INDEX,CREATE_VECTOR,VECTOR_SIMILARITY_SEARCH")
    public static final String OPERATION = "CamelNeo4jOperation";
    @Metadata(description = "MATCH properties for the generated MATCH query of RETRIEVE_NODES and DELETE_NODE, as a JSON object"
                            + " of the property names and values. Needed only if we are matching properties and values.",
              javaType = "String")
    public static final String MATCH_PROPERTIES = "CamelNeo4jMatchProperties";
    @Metadata(description = "The text of the Cypher query that the operation ran, set by CREATE_NODE, DELETE_NODE,"
                            + " ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY, CREATE_VECTOR_INDEX, DROP_VECTOR_INDEX and CREATE_VECTOR",
              javaType = "String")
    public static final String QUERY_RESULT = "CamelNeo4jQueryResult";
    @Metadata(description = "Query Number of nodes created", javaType = "Integer")
    public static final String QUERY_RESULT_NODES_CREATED = "CamelNeo4jQueryResultNodesCreated";
    @Metadata(description = "Query Number of nodes deleted", javaType = "Integer")
    public static final String QUERY_RESULT_NODES_DELETED = "CamelNeo4jQueryResultNodesDeleted";
    @Metadata(description = "Query executed contains update", javaType = "Boolean")
    public static final String QUERY_RESULT_CONTAINS_UPDATES = "CamelNeo4jQueryResultContainsUpdates";
    @Metadata(description = "Query executed number of relationships created", javaType = "Integer")
    public static final String QUERY_RESULT_RELATIONSHIPS_CREATED = "CamelNeo4jQueryResultRelationshipsCreated";
    @Metadata(description = "Query executed number of relationships deleted", javaType = "Integer")
    public static final String QUERY_RESULT_RELATIONSHIPS_DELETED = "CamelNeo4jQueryResultRelationshipsDeleted";
    @Metadata(description = "The number of records that RETRIEVE_NODES, RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY or"
                            + " VECTOR_SIMILARITY_SEARCH retrieved",
              javaType = "Integer")
    public static final String QUERY_RETRIEVE_SIZE = "CamelNeo4jQueryResultRetrieveSize";
    @Metadata(description = "The records that RETRIEVE_NODES, RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY or"
                            + " VECTOR_SIMILARITY_SEARCH retrieved, as the Records of the Neo4j driver, with all their columns",
              javaType = "java.util.List<org.neo4j.driver.Record>")
    public static final String QUERY_RETRIEVE_LIST_NEO4J_NODES = "CamelNeo4jQueryResultListNeo4jNodes";
    @Metadata(description = "Vector Id for the embedding", javaType = "String")
    public static final String VECTOR_ID = "CamelNeo4jVectorEmbeddingId";
    /**
     * @deprecated not used: CREATE_VECTOR takes the label of the node from the label option, and Embedding when the
     *             option is not set.
     */
    @Deprecated(since = "4.24")
    @Metadata(description = "Label for the Node -  used when inserting from Embeddings", javaType = "String",
              deprecationNote = "Not used: the producer does not read this header. CREATE_VECTOR takes the label of the"
                                + " node from the label option, and Embedding when the option is not set")
    public static final String LABEL = "CamelNeo4jLabel";

}
