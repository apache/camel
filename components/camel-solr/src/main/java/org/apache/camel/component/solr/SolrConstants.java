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

public final class SolrConstants {

    @Metadata(description = "The client.", javaType = "org.apache.solr.client.solrj.SolrClient")
    public static final String CLIENT = "CamelSolrClient";
    @Metadata(description = "The collection to execute the request again.", javaType = "String")
    public static final String COLLECTION = "CamelSolrCollection";
    public static final String FIELD = "SolrField.";
    @Metadata(description = "The operation to perform.", javaType = "String")
    public static final String OPERATION = "SolrOperation";
    public static final String OPERATION_COMMIT = "COMMIT";
    public static final String OPERATION_SOFT_COMMIT = "SOFT_COMMIT";
    public static final String OPERATION_ROLLBACK = "ROLLBACK";
    public static final String OPERATION_OPTIMIZE = "OPTIMIZE";
    public static final String OPERATION_INSERT = "INSERT";
    public static final String OPERATION_INSERT_STREAMING = "INSERT_STREAMING";
    public static final String OPERATION_ADD_BEAN = "ADD_BEAN";
    public static final String OPERATION_ADD_BEANS = "ADD_BEANS";
    public static final String OPERATION_DELETE_BY_ID = "DELETE_BY_ID";
    public static final String OPERATION_DELETE_BY_QUERY = "DELETE_BY_QUERY";
    public static final String OPERATION_QUERY = "QUERY";
    public static final String PARAM = "SolrParam.";
    @Metadata(description = "The query to execute.", javaType = "String")
    public static final String QUERY_STRING = "CamelSolrQueryString";
    @Metadata(description = "The content type.", javaType = "String")
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;

    public static final int DEFUALT_STREAMING_QUEUE_SIZE = 10;
    public static final int DEFAULT_STREAMING_THREAD_COUNT = 2;

    private SolrConstants() {
        throw new AssertionError();
    }
}
