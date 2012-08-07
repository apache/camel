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
package org.apache.camel.component.neo4j;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.neo4j.graphdb.GraphDatabaseService;
import org.springframework.data.neo4j.rest.SpringRestGraphDatabase;

public class Neo4jEndpoint extends DefaultEndpoint {

    public static final String HEADER_OPERATION = "Neo4jOperation";
    public static final String HEADER_NODE_ID = "Neo4jNodeId";
    public static final String HEADER_RELATIONSHIP_ID = "Neo4jRelationshipId";

    private final GraphDatabaseService graphDatabase;

    public Neo4jEndpoint(String endpointUri, String remaining, Neo4jComponent component) {
        super(endpointUri, component);
        graphDatabase = new SpringRestGraphDatabase(remaining);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Neo4jProducer(this, (SpringRestGraphDatabase)graphDatabase);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
