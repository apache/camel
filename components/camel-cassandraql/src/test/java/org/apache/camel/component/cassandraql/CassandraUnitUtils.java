/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cassandraql;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.CQLDataSet;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;

/**
 * Util methods to manage Cassandra in Unit tests
 */
public class CassandraUnitUtils {

    private static CassandraCQLUnit cassandraCQLUnit;
    /**
     * Create Cassandra JUnit Rule.
     */
    public static CassandraCQLUnit cassandraCQLUnit() {
        if (cassandraCQLUnit == null) {
            cassandraCQLUnit = cassandraCQLUnit(new ClassPathCQLDataSet("BasicDataSet.cql", "camel_ks"));
        }
        return cassandraCQLUnit;
    }

    public static CassandraCQLUnit cassandraCQLUnit(CQLDataSet dataset) {
        return new CassandraCQLUnit(dataset, "/camel-cassandra.yaml", "localhost", 9042);
    }
    /**
     * Start embedded Cassandra.
     */
    public static void startEmbeddedCassandra() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra("/camel-cassandra.yaml", "target/camel-cassandra");
    }

    /**
     * Clean embedded Cassandra.
     */
    public static void cleanEmbeddedCassandra() throws Exception {
        EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
    }
    public static Session connectCassandra() {
        Cluster cluster = Cluster.builder()
                .addContactPoint("localhost")
                .withClusterName("camel-cluster")
                .build();
        return cluster.connect("camel_ks");
    }
}
