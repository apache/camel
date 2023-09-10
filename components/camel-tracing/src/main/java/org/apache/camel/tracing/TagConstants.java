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

public class TagConstants {

    public static final String ERROR = "error";
    public static final String COMPONENT = "component";

    // General attributes
    public static final String SERVER_ADDRESS = "server.address";

    // HTTP attributes
    public static final String HTTP_STATUS = "http.status_code";
    public static final String HTTP_METHOD = "http.method";
    public static final String HTTP_URL = "http.url";
    public static final String URL_SCHEME = "url.scheme";
    public static final String URL_PATH = "url.path";
    public static final String URL_QUERY = "url.query";

    // Messaging attributes
    public static final String MESSAGE_BUS_DESTINATION = "messaging.destination.name";
    public static final String MESSAGE_ID = "messaging.message.id";

    // Database attributes
    public static final String DB_SYSTEM = "db.system";
    public static final String DB_NAME = "db.name";
    public static final String DB_STATEMENT = "db.statement";

    // Exception attributes
    public static final String EXCEPTION_ESCAPED = "exception.escaped";
    public static final String EXCEPTION_MESSAGE = "exception.message";
}
