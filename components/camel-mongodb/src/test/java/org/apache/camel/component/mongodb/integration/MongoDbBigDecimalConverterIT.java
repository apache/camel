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

import java.math.BigDecimal;

import com.mongodb.BasicDBObject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoDbBigDecimalConverterIT extends AbstractMongoDbITSupport implements ConfigurableRoute {

    private class NumberClass {
        public String _id = "testBigDecimalConvert";

        public BigDecimal aNumber = new BigDecimal(0);

        public BigDecimal bNumber = new BigDecimal(12345L);
    }

    @Test
    public void testBigDecimalAutoConversion() {
        assertEquals(0, testCollection.countDocuments());
        NumberClass testClass = new NumberClass();
        Object result = template.requestBody("direct:insert", testClass);
        assertTrue(result instanceof Document);
        Document b = testCollection.find(new BasicDBObject("_id", testClass._id)).first();
        assertNotNull(b, "No record with 'testInsertString' _id");

        assertEquals(new BigDecimal((double) b.get("aNumber")), testClass.aNumber);
        assertEquals(testClass.bNumber, new BigDecimal((double) b.get("bNumber")));
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:insert")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");
            }
        };
    }
}
