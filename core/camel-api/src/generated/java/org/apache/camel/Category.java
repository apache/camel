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
package org.apache.camel;

/**
 * This enum set various categories options into the UriEndpoint. This can be extended.
 */
public enum Category {
    AI("ai"),
    API("api"),
    BIGDATA("bigdata"),
    BLOCKCHAIN("blockchain"),
    CACHE("cache"),
    CHAT("chat"),
    CLOUD("cloud"),
    CLUSTERING("clustering"),
    CMS("cms"),
    CONTAINER("container"),
    CORE("core"),
    DATABASE("database"),
    DOCUMENT("document"),
    FILE("file"),
    HEALTH("HEALTH"),
    HTTP("http"),
    IOT("iot"),
    MAIL("mail"),
    MANAGEMENT("management"),
    MESSAGING("messaging"),
    MOBILE("mobile"),
    MONITORING("monitoring"),
    NETWORKING("networking"),
    REST("rest"),
    RPC("rpc"),
    SAAS("saas"),
    SCHEDULING("scheduling"),
    SCRIPT("script"),
    SEARCH("search"),
    SECURITY("security"),
    SERVERLESS("serverless"),
    SOCIAL("social"),
    TESTING("testing"),
    TRANSFORMATION("transformation"),
    VALIDATION("validation"),
    WEBSERVICE("webservice"),
    WORKFLOW("workflow");

    private final String value;

    Category(final String value) {
        this.value = value;
    }

    /**
     * Returns the string representation of this value
     *
     * @return Returns the string representation of this value
     */
    public String getValue() {
        return this.value;
    }
}
