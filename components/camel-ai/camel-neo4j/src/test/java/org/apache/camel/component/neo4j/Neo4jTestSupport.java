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
package org.apache.camel.component.neo4j;

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.neo4j.services.Neo4jService;
import org.apache.camel.test.infra.neo4j.services.Neo4jServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Neo4jTestSupport extends CamelTestSupport {

    @RegisterExtension
    static Neo4jService NEO4J = Neo4jServiceFactory.createSingletonService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        Neo4jComponent component = context.getComponent(Neo4jConstants.SCHEME, Neo4jComponent.class);
        component.getConfiguration().setDatabaseUrl(NEO4J.getNeo4jDatabaseUri());
        component.getConfiguration().setUsername(NEO4J.getNeo4jDatabaseUser());
        component.getConfiguration().setPassword(NEO4J.getNeo4jDatabasePassword());
        return context;
    }
}
