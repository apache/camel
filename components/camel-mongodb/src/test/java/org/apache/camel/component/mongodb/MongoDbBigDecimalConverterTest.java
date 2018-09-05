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
package org.apache.camel.component.mongodb;

import java.math.BigDecimal;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MongoDbBigDecimalConverterTest extends AbstractMongoDbTest {

    private class NumberClass {
        // CHECKSTYLE:OFF
        public String _id = "testBigDecimalConvert";
        // CHECKSTYLE:ON

        public BigDecimal aNumber = new BigDecimal(0);

        public BigDecimal bNumber = new BigDecimal(12345L);
    }
    @Test
    public void testBigDecimalAutoConversion() {
        assertEquals(0, testCollection.count());
        NumberClass testClass = new NumberClass();
        Object result = template.requestBody("direct:insert", testClass);
        assertTrue(result instanceof BasicDBObject);
        DBObject b = testCollection.find(new BasicDBObject("_id", testClass._id)).first();
        assertNotNull("No record with 'testInsertString' _id", b);

        assertTrue(testClass.aNumber.equals(new BigDecimal((double) b.get("aNumber"))));
        assertEquals(testClass.bNumber, new BigDecimal((double) b.get("bNumber")));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:insert")
                    .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&writeConcern=SAFE");
            }
        };
    }
}

