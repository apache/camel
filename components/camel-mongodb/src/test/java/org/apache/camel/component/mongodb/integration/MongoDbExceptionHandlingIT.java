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
package org.apache.camel.component.mongodb.integration;

import com.mongodb.DBObject;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mongodb.MongoDbConstants;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MongoDbExceptionHandlingIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    @BeforeEach
    void checkDocuments() {
        Assumptions.assumeTrue(0 == testCollection.countDocuments(), "The collection should have no documents");
    }

    @Test
    public void testInduceParseException() {
        pumpDataIntoTestCollection();

        // notice missing quote at the end of Einstein
        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:findOneByQuery", "{\"scientist\": \"Einstein}"),
                "Should have thrown an exception");
        extractAndAssertCamelMongoDbException(ex, null);
    }

    @Test
    public void testInduceParseAndThenOkException() {
        pumpDataIntoTestCollection();

        // notice missing quote at the end of Einstein
        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:findOneByQuery", "{\"scientist\": \"Einstein}"),
                "Should have thrown an exception");
        extractAndAssertCamelMongoDbException(ex, null);

        // this one is okay
        DBObject out = template.requestBody("direct:findOneByQuery", "{\"scientist\": \"Einstein\"}", DBObject.class);
        assertNotNull(out);
        assertEquals("Einstein", out.get("scientist"));
    }

    @Test
    public void testErroneousDynamicOperation() {
        pumpDataIntoTestCollection();

        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader("direct:findOneByQuery", new Document("scientist", "Einstein").toJson(),
                        MongoDbConstants.OPERATION_HEADER, "dummyOp"),
                "Should have thrown an exception");

        extractAndAssertCamelMongoDbException(ex, "Operation specified on header is not supported. Value: dummyOp");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("direct:findAll").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findAll&dynamicity=true")
                        .to("mock:resultFindAll");

                from("direct:findOneByQuery").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findOneByQuery&dynamicity=true")
                        .to("mock:resultFindOneByQuery");

                from("direct:findById").to(
                        "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findById&dynamicity=true")
                        .to("mock:resultFindById");

            }
        };
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }
}
