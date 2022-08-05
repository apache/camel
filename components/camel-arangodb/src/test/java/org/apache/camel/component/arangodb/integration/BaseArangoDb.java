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

import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.DbName;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.arangodb.services.ArangoDBService;
import org.apache.camel.test.infra.arangodb.services.ArangoDBServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public class BaseArangoDb extends CamelTestSupport {
    @RegisterExtension
    public static ArangoDBService service = ArangoDBServiceFactory.createService();

    protected static final String DATABASE_NAME = "dbTest";
    protected static final String COLLECTION_NAME = "camelTest";
    protected static final String GRAPH_NAME = "graphTest";
    protected static final String VERTEX_COLLECTION_NAME = "vertexTest";
    protected static final String EDGE_COLLECTION_NAME = "edgeTest";
    protected static ArangoDB arangoDb;
    protected static ArangoDatabase arangoDatabase;

    @BeforeAll
    public static void doBeforeAll() {
        arangoDb = new ArangoDB.Builder().build();
        arangoDb.createDatabase(DbName.of(DATABASE_NAME));
        arangoDatabase = arangoDb.db(DbName.of(DATABASE_NAME));
    }

    @AfterAll
    public static void doAfterAll() {
        arangoDb.shutdown();
    }

    @Override
    protected CamelContext createCamelContext() {
        CamelContext ctx = new DefaultCamelContext();
        ctx.getPropertiesComponent().setLocation("classpath:arango.test.properties");
        return ctx;
    }
}
