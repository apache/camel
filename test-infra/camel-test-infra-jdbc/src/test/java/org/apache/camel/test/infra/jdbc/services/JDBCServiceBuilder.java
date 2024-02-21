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

package org.apache.camel.test.infra.jdbc.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;

@Deprecated
public final class JDBCServiceBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(JDBCServiceBuilder.class);

    private JdbcDatabaseContainer<?> container;

    protected JDBCServiceBuilder() {
    }

    public static JDBCServiceBuilder newBuilder() {
        JDBCServiceBuilder jdbcServiceBuilder = new JDBCServiceBuilder();

        return jdbcServiceBuilder;
    }

    public JDBCServiceBuilder withContainer(JdbcDatabaseContainer<?> container) {
        this.container = container;

        return this;
    }

    public JDBCService build() {
        String instanceType = System.getProperty("jdbc.instance.type");

        if (instanceType == null || instanceType.isEmpty()) {
            LOG.info("Creating a new messaging local container service");
            return new JDBCLocalContainerService(container);
        }

        if (instanceType.equals("remote")) {
            return new JDBCRemoteService();
        }

        throw new UnsupportedOperationException("Invalid messaging instance type");
    }
}
