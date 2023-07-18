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
package org.apache.camel.component.arangodb.integration;

import com.arangodb.ArangoCollection;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.arangodb.services.ArangoDBService;
import org.apache.camel.test.infra.arangodb.services.ArangoDBServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;

public class BaseArangoDb extends CamelTestSupport {

    @RegisterExtension
    public static ArangoDBService service = ArangoDBServiceFactory.createService();

    protected static final String DATABASE_NAME = "dbTest";
    protected static final String COLLECTION_NAME = "camelTest";
    protected static final String GRAPH_NAME = "graphTest";
    protected static final String VERTEX_COLLECTION_NAME = "vertexTest";
    protected static final String EDGE_COLLECTION_NAME = "edgeTest";
    protected ArangoDB arangoDb;
    protected ArangoDatabase arangoDatabase;
    protected ArangoCollection collection;

    @Override
    protected CamelContext createCamelContext() {
        CamelContext ctx = new DefaultCamelContext();

        arangoDb = new ArangoDB.Builder().host("localhost", 8529).build();

        // drop any existing database to start clean
        if (arangoDb.getDatabases().contains(DATABASE_NAME)) {
            arangoDatabase = arangoDb.db(DATABASE_NAME);
            arangoDatabase.drop();
        }

        arangoDb.createDatabase(DATABASE_NAME);
        arangoDatabase = arangoDb.db(DATABASE_NAME);
        arangoDatabase.createCollection(COLLECTION_NAME);
        collection = arangoDatabase.collection(COLLECTION_NAME);

        ctx.getRegistry().bind("arangoDB", arangoDb);
        ctx.getPropertiesComponent().setLocation("classpath:arango.test.properties");
        return ctx;
    }
}
