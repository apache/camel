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
package org.apache.camel.test.infra.cassandra.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A remote instance of Apache Cassandra
 */
public class RemoteCassandraService implements CassandraService<RemoteCassandraService> {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteCassandraService.class);

    private static final int DEFAULT_CQL_PORT = 9042;

    @Override
    public RemoteCassandraService withInitScript(String initScript) {
        LOG.warn("The init script is not supported on the remote cassandra service.");
        return this;
    }

    @Override
    public RemoteCassandraService withNetworkAliases(String network) {
        LOG.warn("The network aliases configuration is not supported on the remote cassandra service.");

        return this;
    }

    @Override
    public int getCQL3Port() {
        String strPort = System.getProperty("cassandra.cql3.port");

        if (strPort != null) {
            return Integer.parseInt(strPort);
        }

        return DEFAULT_CQL_PORT;
    }

    @Override
    public String getCassandraHost() {
        return System.getProperty("cassandra.host");
    }

    @Override
    public void initialize() {
        // NO-OP
    }

    @Override
    public void shutdown() {
        // NO-OP
    }
}
