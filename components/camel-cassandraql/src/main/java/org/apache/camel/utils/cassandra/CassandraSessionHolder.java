/**
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
package org.apache.camel.utils.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * Holds a Cassandra Session and manages its lifecycle
 */
public class CassandraSessionHolder {
    /**
     * Cluster
     */
    private final Cluster cluster;
    /**
     * Session
     */
    private Session session;
    /**
     * Keyspace name
     */
    private String keyspace;
    /**
     * Indicates whether Session is externally managed
     */
    private final boolean managedSession;

    public CassandraSessionHolder(Cluster cluster, String keyspace) {
        this.cluster = cluster;
        this.keyspace = keyspace;
        this.managedSession = true;
    }

    public CassandraSessionHolder(Session session) {
        this.cluster = session.getCluster();
        this.session = session;
        this.keyspace = session.getLoggedKeyspace();
        this.managedSession = false;
    }

    public void start() {
        if (managedSession) {
            if (keyspace == null) {
                this.session = cluster.connect();
            } else {
                this.session = cluster.connect(keyspace);
            }
        }
    }

    public void stop() {
        if (!managedSession) {
            session.close();
            session = null;
        }
    }

    public Session getSession() {
        return session;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public String getKeyspace() {
        return keyspace;
    }

}
