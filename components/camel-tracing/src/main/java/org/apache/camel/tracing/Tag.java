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

@Deprecated
public enum Tag {
    COMPONENT(TagConstants.COMPONENT),
    ERROR(TagConstants.ERROR),

    // General Tags
    SERVER_ADDRESS(TagConstants.SERVER_ADDRESS),

    // HTTP tags
    HTTP_STATUS(TagConstants.HTTP_STATUS),
    HTTP_METHOD(TagConstants.HTTP_METHOD),
    HTTP_URL(TagConstants.HTTP_URL),
    URL_SCHEME(TagConstants.URL_SCHEME),
    URL_PATH(TagConstants.URL_PATH),
    URL_QUERY(TagConstants.URL_QUERY),

    // Messaging tags
    MESSAGE_BUS_DESTINATION(TagConstants.MESSAGE_BUS_DESTINATION),
    MESSAGE_ID(TagConstants.MESSAGE_ID),

    // Database tags
    DB_TYPE(TagConstants.DB_SYSTEM),
    DB_INSTANCE(TagConstants.DB_NAME),
    DB_STATEMENT(TagConstants.DB_STATEMENT),

    // Exception tags
    EXCEPTION_MESSAGE(TagConstants.EXCEPTION_MESSAGE);

    Tag(String attribute) {
        this.attribute = attribute;
    }

    private final String attribute;

    public String getAttribute() {
        return attribute;
    }
}
