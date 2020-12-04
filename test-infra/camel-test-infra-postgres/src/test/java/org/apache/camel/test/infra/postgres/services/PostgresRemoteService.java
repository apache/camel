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
package org.apache.camel.test.infra.postgres.services;

import org.apache.camel.test.infra.postgres.common.PostgresProperties;

public class PostgresRemoteService implements PostgresService {

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {
        // NO-OP
    }

    @Override
    public String host() {
        return System.getProperty(PostgresProperties.HOST);
    }

    @Override
    public int port() {
        String port = System.getProperty(PostgresProperties.PORT);

        if (port == null) {
            return PostgresProperties.DEFAULT_PORT;
        }

        return Integer.valueOf(port);
    }

    @Override
    public String getServiceAddress() {
        return System.getProperty(PostgresProperties.SERVICE_ADDRESS);
    }

    @Override
    public String userName() {
        return System.getProperty(PostgresProperties.USERNAME);
    }

    @Override
    public String password() {
        return System.getProperty(PostgresProperties.PASSWORD);
    }
}
