/**
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

public final class SolrConstants {

    public static final String FIELD = "SolrField.";
    public static final String OPERATION = "SolrOperation";
    public static final String PARAM = "SolrParam.";
    public static final String OPERATION_COMMIT = "COMMIT";
    public static final String OPERATION_ROLLBACK = "ROLLBACK";
    public static final String OPERATION_OPTIMIZE = "OPTIMIZE";
    public static final String OPERATION_INSERT = "INSERT";
    public static final String OPERATION_ADD_BEAN = "ADD_BEAN";
    public static final String OPERATION_DELETE_BY_ID = "DELETE_BY_ID";
    public static final String OPERATION_DELETE_BY_QUERY = "DELETE_BY_QUERY";

    private SolrConstants() {
        throw new AssertionError();
    }
}
