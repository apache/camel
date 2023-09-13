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
package org.apache.camel.tracing;

public enum Tag {
    COMPONENT("component"),
    ERROR("error"),

    // General Tags
    SERVER_ADDRESS("server.address"),

    // HTTP tags
    HTTP_STATUS("http.status_code"),
    HTTP_METHOD("http.method"),
    HTTP_URL("http.url"),
    URL_SCHEME("url.scheme"),
    URL_PATH("url.path"),
    URL_QUERY("url.query"),

    // Messaging tags
    MESSAGE_BUS_DESTINATION("messaging.destination.name"),
    MESSAGE_ID("messaging.message.id"),

    // Database tags
    DB_TYPE("db.system"),
    DB_INSTANCE("db.name"),
    DB_STATEMENT("db.statement"),

    // Exception tags
    EXCEPTION_MESSAGE("exception.message");

    Tag(String attribute) {
        this.attribute = attribute;
    }

    private final String attribute;

    public String getAttribute() {
        return attribute;
    }
}
