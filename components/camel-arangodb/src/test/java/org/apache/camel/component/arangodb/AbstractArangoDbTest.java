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
package org.apache.camel.component.arangodb;

import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class AbstractArangoDbTest extends CamelTestSupport {


    protected static final String DATABASE_NAME = "dbTest";
    protected static final String COLLECTION_NAME = "camelTest";
    protected static ArangoDbContainer container;
    protected static ArangoDB arangoDb;
    protected static ArangoDatabase arangoDatabase;

    @BeforeAll
    public static void doBeforeAll() {
        container = new ArangoDbContainer();
        container.start();
        arangoDb = new ArangoDB.Builder().build();
        arangoDb.createDatabase(DATABASE_NAME);
        arangoDatabase = arangoDb.db(DATABASE_NAME);
    }

    @AfterAll
    public static void doAfterAll() {
        arangoDb.shutdown();
        if (container != null) {
            container.stop();
        }
    }

    @Override
    protected CamelContext createCamelContext() {
        CamelContext ctx = new DefaultCamelContext();
        ctx.getPropertiesComponent().setLocation("classpath:arango.test.properties");
        return ctx;
    }
}
