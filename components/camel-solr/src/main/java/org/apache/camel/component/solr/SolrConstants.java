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

    @Metadata(description = "The operation to perform: INSERT, DELETE, SEARCH or PING. It replaces the operation option, and a"
                            + " body that is a SolrJ request replaces it. Older values such as DELETE_BY_QUERY or COMMIT still"
                            + " work, and log a warning.",
              javaType = "String")
    String PARAM_OPERATION = "CamelSolrOperation";
    @Metadata(description = "The name of the collection that the request goes to. It replaces the collection option.",
              javaType = "String")
    String PARAM_COLLECTION = "CamelSolrCollection";
    @Metadata(description = "The path of the request handler, for example /update/json. It replaces the requestHandler option.",
              javaType = "String")
    String PARAM_REQUEST_HANDLER = "CamelSolrRequestHandler";
    @Metadata(description = "The query of a search. It replaces a text body, but not a SolrQuery or QueryRequest body. When no"
                            + " operation is set, a message with this header is a search.",
              javaType = "String")
    String PARAM_QUERY_STRING = "CamelSolrQueryString";
    @Metadata(description = "The maximum number of documents that a search returns, which is the rows parameter of Solr. It"
                            + " replaces the size option.",
              javaType = "Integer")
    String PARAM_SIZE = "CamelSolrSize";
    @Metadata(description = "The index of the first document that a search returns, which is the start parameter of Solr. It"
                            + " replaces the from option.",
              javaType = "Integer")
    String PARAM_FROM = "CamelSolrFrom";
    @Metadata(description = "The request parameters to send to Solr with an insert, a delete or a search. The headers with the"
                            + " CamelSolrParam. prefix add to them.",
              javaType = "org.apache.solr.common.params.SolrParams")
    String PARAM_SOLR_PARAMS = "CamelSolrParams";
    @Metadata(description = "Whether a DELETE takes the body as a query, or a collection of queries, instead of the id of the"
                            + " documents to delete. It replaces the deleteByQuery option.",
              javaType = "boolean", defaultValue = "false")
    String PARAM_DELETE_BY_QUERY = "CamelSolrDeleteByQuery";
    @Metadata(description = "The content type of a file or text body to insert, for example application/json. If not set, it"
                            + " is detected from the file or the text.",
              javaType = "String")
    String PARAM_CONTENT_TYPE = Exchange.CONTENT_TYPE;

    @Metadata(description = "The prefix of the headers that carry the fields of the document to insert: the header"
                            + " CamelSolrField.id holds the id field.",
              javaType = "Object")
    String HEADER_FIELD_PREFIX = "CamelSolrField.";
    @Metadata(description = "The prefix of the headers that carry extra request parameters: the header"
                            + " CamelSolrParam.commit holds the commit parameter.",
              javaType = "String")
    String HEADER_PARAM_PREFIX = "CamelSolrParam.";

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
