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
package org.apache.camel.component.gora;

/**
 * Camel-Gora Operations.
 */
public enum GoraOperation {

    /**
     * Gora "put" operation
     */
    PUT("put"),

    /**
     * Gora fetch/"get" operation
     */
    GET("get"),

    /**
     * Gora "delete" operation
     */
    DELETE("delete"),

    /**
     * Gora "get schema name" operation
     */
    GET_SCHEMA_NAME("getSchemaName"),

    /**
     * Gora "delete schema" operation
     */
    DELETE_SCHEMA("deleteSchema"),

    /**
     * Gora "create schema" operation
     */
    CREATE_SCHEMA("createSchema"),

    /**
     * Gora "query" operation
     */
    QUERY("query"),

    /**
     * Gora "deleteByQuery" operation
     */
    DELETE_BY_QUERY("deleteByQuery"),

    /**
     * Gora "schemaExists" operation
     */
    SCHEMA_EXIST("schemaExists");

    /**
     * Enum value
     */
    public final String value;

    /**
     * Enum constructor
     */
    GoraOperation(final String str) {
        value = str;
    }
}
