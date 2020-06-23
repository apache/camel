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
package org.apache.camel.component.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.dataset.CQLDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class CassandraCQLUnit implements BeforeAllCallback, BeforeEachCallback {

    public Session session;
    public Cluster cluster;
    protected CQLDataSet dataSet;
    protected String configurationFileName;
    protected long startupTimeoutMillis = EmbeddedCassandraServerHelper.DEFAULT_STARTUP_TIMEOUT;

    public CassandraCQLUnit(CQLDataSet dataSet, String configurationFileName) {
        this.dataSet = dataSet;
        this.configurationFileName = configurationFileName;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        assumeTrue(BaseCassandraTest.canTest(),
                "Skipping test running in CI server - Fails sometimes on CI server with address already in use");

        /* start an embedded Cassandra */
        if (configurationFileName != null) {
            EmbeddedCassandraServerHelper.startEmbeddedCassandra(configurationFileName, startupTimeoutMillis);
        } else {
            EmbeddedCassandraServerHelper.startEmbeddedCassandra(startupTimeoutMillis);
        }

        /* create structure and load data */
        cluster = EmbeddedCassandraServerHelper.getCluster();
        session = EmbeddedCassandraServerHelper.getSession();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        CQLDataLoader dataLoader = new CQLDataLoader(session);
        dataLoader.load(dataSet);
    }

}
