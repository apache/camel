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

package org.apache.camel.test.infra.postgres.common;

public final class PostgresProperties {
    public static final String SERVICE_ADDRESS = "postgres.service.address";
    public static final String HOST = "postgres.service.host";
    public static final String PORT = "postgres.service.port";
    public static final String USERNAME = "postgres.user.name";
    public static final String PASSWORD = "postgres.user.password";

    public static final int DEFAULT_PORT = 5432;

    private PostgresProperties() {

    }
}
