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
package org.apache.camel.component.solr;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public interface SolrConstants {

    @Metadata(description = "The operation to perform.", javaType = "String")
    String PARAM_OPERATION = "operation";
    @Deprecated
    @Metadata(description = "The operation to perform.", javaType = "String",
              deprecationNote = "Use header 'operation' instead")
    String PARAM_OPERATION_DEPRECATED = "SolrOperation";
    @Metadata(description = "The collection to execute the request against.", javaType = "String")
    String PARAM_COLLECTION = "collection";
    @Deprecated
    @Metadata(description = "The collection to execute the request against.", javaType = "String",
              deprecationNote = "Use header 'collection' instead")
    String PARAM_COLLECTION_DEPRECATED = "CamelSolrCollection";
    @Metadata(description = "The request handler to execute the solr request against.", javaType = "String")
    String PARAM_REQUEST_HANDLER = "requestHandler";
    @Metadata(description = "The query to execute.", javaType = "String")
    String PARAM_QUERY_STRING = "queryString";
    @Deprecated
    @Metadata(description = "The query to execute.", javaType = "String", deprecationNote = "Use header 'queryString' instead")
    String PARAM_QUERY_STRING_DEPRECATED = "CamelSolrQueryString";
    @Metadata(description = "The size of the response.", javaType = "Integer")
    String PARAM_SIZE = "size";
    @Metadata(description = "The starting index of the response.", javaType = "Integer")
    String PARAM_FROM = "from";
    @Metadata(description = "The solr client to use for the request.", javaType = "SolrClient")
    String PARAM_SOLR_CLIENT = "solrClient";
    @Deprecated
    @Metadata(description = "The solr client to use for the request.", javaType = "String",
              deprecationNote = "Use header 'solrClient' instead")
    String PARAM_SOLR_CLIENT_DEPRECATED = "CamelSolrClient";
    @Metadata(description = "The solr parameters to use for the request.", javaType = "SolrParams")
    String PARAM_SOLR_PARAMS = "solrParams";
    @Metadata(description = "For the delete instruction, interprete body as query/queries instead of id/ids.",
              javaType = "boolean", defaultValue = "false")
    String PARAM_DELETE_BY_QUERY = "deleteByQuery";
    @Metadata(description = "The content type is used to identify the type when inserting files.", javaType = "String")
    String PARAM_CONTENT_TYPE = Exchange.CONTENT_TYPE;

    String HEADER_FIELD_PREFIX = "SolrField.";
    String HEADER_PARAM_PREFIX = "SolrParam.";

    String PROPERTY_ACTION_CONTEXT = "SolrActionContext";

    String OPERATION_COMMIT = "COMMIT";
    String OPERATION_SOFT_COMMIT = "SOFT_COMMIT";
    String OPERATION_ROLLBACK = "ROLLBACK";
    String OPERATION_OPTIMIZE = "OPTIMIZE";
    String OPERATION_INSERT = "INSERT";
    String OPERATION_INSERT_STREAMING = "INSERT_STREAMING";
    String OPERATION_ADD_BEAN = "ADD_BEAN";
    String OPERATION_ADD_BEANS = "ADD_BEANS";
    String OPERATION_DELETE_BY_ID = "DELETE_BY_ID";
    String OPERATION_DELETE_BY_QUERY = "DELETE_BY_QUERY";
    String OPERATION_QUERY = "QUERY";
    String OPERATION_SEARCH = "SEARCH";

    String HEADER_PARAM_OPERATION_COMMIT = "commit";
    String HEADER_PARAM_OPERATION_SOFT_COMMIT = "softCommit";
    String HEADER_PARAM_OPERATION_OPTIMIZE = "optimize";
    String HEADER_PARAM_OPERATION_ROLLBACK = "rollback";

    String DEFAULT_HOST = "localhost";
    int DEFAULT_PORT = 8983;
    String DEFAULT_BASE_PATH = "/solr";
    String DEFAULT_COLLECTION = "collection1";

    int DEFAULT_CONNECT_TIMEOUT = 60000;
    int DEFAULT_REQUEST_TIMEOUT = 600000;

}
