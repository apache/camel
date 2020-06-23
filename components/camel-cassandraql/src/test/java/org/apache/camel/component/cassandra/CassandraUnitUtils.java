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
import org.cassandraunit.dataset.CQLDataSet;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;

/**
 * Util methods to manage Cassandra in Unit tests
 */
public final class CassandraUnitUtils {
    public static final String HOST = "127.0.0.1";
    public static final String KEYSPACE = "camel_ks";

    private CassandraUnitUtils() {
    }

    /**
     * Create Cassandra JUnit Rule.
     */
    public static CassandraCQLUnit cassandraCQLUnit() {
        return cassandraCQLUnit("BasicDataSet.cql");
    }

    public static CassandraCQLUnit cassandraCQLUnit(String dataSetCql) {
        return cassandraCQLUnit(cqlDataSet(dataSetCql));
    }

    public static CQLDataSet cqlDataSet(String dataSetCql) {
        return new ClassPathCQLDataSet(dataSetCql, KEYSPACE);
    }

    public static CassandraCQLUnit cassandraCQLUnit(CQLDataSet dataset) {
        return cassandraCQLUnit(dataset, "/camel-cassandra.yaml");
    }

    public static CassandraCQLUnit cassandraCQLUnit(CQLDataSet dataset, String configurationFileName) {
        return new CassandraCQLUnit(dataset, "/camel-cassandra.yaml");
    }

    /**
     * Start embedded Cassandra.
     */
    public static void startEmbeddedCassandra() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra("/camel-cassandra.yaml", "target/camel-cassandra", 30000);
    }

    /**
     * Clean embedded Cassandra.
     */
    public static void cleanEmbeddedCassandra() throws Exception {
        EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
    }

    public static Cluster cassandraCluster() {
        return Cluster.builder().addContactPoint(HOST).withClusterName("camel-cluster").build();
    }
}
