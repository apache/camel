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

package org.apache.camel.processor.aggregate.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

/**
 * Concrete implementation of {@link  CassandraAggregationRepository} using 2 
 * columns as primary key: name (partition key) and key (clustering key).
 */
public class NamedCassandraAggregationRepository extends CassandraAggregationRepository {
    /**
     * Aggregation repository name
     */
    private String name;
    public NamedCassandraAggregationRepository() {
        setPKColumns("NAME", "KEY");
    }
    public NamedCassandraAggregationRepository(Session session, String name) {
        super(session);
        this.name = name;
        setPKColumns("NAME", "KEY");
    }
    public NamedCassandraAggregationRepository(Cluster cluster, String keyspace, String name) {
        super(cluster, keyspace);
        this.name = name;
        setPKColumns("NAME", "KEY");
    }

    @Override
    protected Object[] getPKValues() {
        return new Object[]{name};
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
