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
package org.apache.camel.component.mongodb;

import java.util.ArrayList;
import java.util.List;

import com.mongodb.BasicDBObject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MongoDbInsertBatchTest extends AbstractMongoDbTest {

    @Test
    public void testInsertBatch() {
        assertEquals(0, testCollection.countDocuments());

        Document a = new Document(MongoDbConstants.MONGO_ID, "testInsert1");
        a.append("MyId", 1).toJson();
        Document b = new Document(MongoDbConstants.MONGO_ID, "testInsert2");
        b.append("MyId", 2).toJson();
        Document c = new Document(MongoDbConstants.MONGO_ID, "testInsert3");
        c.append("MyId", 3).toJson();

        List<Document> taxGroupList = new ArrayList<>();
        taxGroupList.add(a);
        taxGroupList.add(b);
        taxGroupList.add(c);

        Exchange out = fluentTemplate.to("direct:insert").withBody(taxGroupList).send();

        List oid = out.getMessage().getHeader(MongoDbConstants.OID, List.class);
        assertNotNull(oid);
        assertEquals(3, oid.size());

        Document out1 = testCollection.find(new BasicDBObject("_id", oid.get(0))).first();
        assertNotNull(out1);
        assertEquals(1, out1.getInteger("MyId"));
        Document out2 = testCollection.find(new BasicDBObject("_id", oid.get(1))).first();
        assertNotNull(out2);
        assertEquals(2, out2.getInteger("MyId"));
        Document out3 = testCollection.find(new BasicDBObject("_id", oid.get(2))).first();
        assertNotNull(out3);
        assertEquals(3, out3.getInteger("MyId"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:insert")
                        .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert");
            }
        };
    }
}
