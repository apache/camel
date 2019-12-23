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

import java.util.Arrays;
import java.util.List;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class MongoDbBulkWriteOperationTest extends AbstractMongoDbTest {

    @Test
    public void testBulkWrite() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        pumpDataIntoTestCollection();
        List<WriteModel<Document>> bulkOperations = Arrays
            .asList(new InsertOneModel<>(new Document("scientist", "Pierre Curie")),
                    new UpdateOneModel<>(new Document("_id", "2"), 
                                         new Document("$set", new Document("scientist", "Charles Darwin"))),
                    new UpdateManyModel<>(new Document("scientist", "Curie"), 
                            new Document("$set", new Document("scientist", "Marie Curie"))),
                    new ReplaceOneModel<>(new Document("_id", "1"), new Document("scientist", "Albert Einstein")),
                    new DeleteOneModel<>(new Document("_id", "3")),
                    new DeleteManyModel<>(new Document("scientist", "Bohr")));

        BulkWriteResult result = template.requestBody("direct:bulkWrite", bulkOperations, BulkWriteResult.class);

        assertNotNull(result);
        // 1 insert
        assertEquals(1, result.getInsertedCount(), "Records inserted should be 2 : ");
        // 1 updateOne + 100 updateMany + 1 replaceOne
        assertEquals(102, result.getMatchedCount(), "Records matched should be 102 : ");
        assertEquals(102, result.getModifiedCount(), "Records modified should be 102 : ");
        // 1 deleteOne + 100 deleteMany
        assertEquals(101, result.getDeletedCount(), "Records deleted should be 101 : ");
    }

    @Test
    public void testOrderedBulkWriteWithError() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        pumpDataIntoTestCollection();

        List<WriteModel<Document>> bulkOperations = Arrays
            .asList(new InsertOneModel<>(new Document("scientist", "Pierre Curie")),
                    // this insert failed and bulk stop
                    new InsertOneModel<>(new Document("_id", "1")), 
                    new InsertOneModel<>(new Document("scientist", "Descartes")),
                    new UpdateOneModel<>(new Document("_id", "5"), new Document("$set", new Document("scientist", "Marie Curie"))),
                    new DeleteOneModel<>(new Document("_id", "2")));

        try {
            template.requestBody("direct:bulkWrite", bulkOperations, BulkWriteResult.class);
            fail("Bulk operation should throw Exception");
        } catch (CamelExecutionException e) {
            extractAndAssertCamelMongoDbException(e, "duplicate key error");
            // count = 1000 records + 1 inserted
            assertEquals(1001, testCollection.countDocuments());
        }
    }

    @Test
    public void testUnorderedBulkWriteWithError() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.countDocuments());
        pumpDataIntoTestCollection();

        List<WriteModel<Document>> bulkOperations = Arrays
            .asList(new InsertOneModel<>(new Document("scientist", "Pierre Curie")),
                    // this insert failed and bulk continue
                    new InsertOneModel<>(new Document("_id", "1")),
                    new InsertOneModel<>(new Document("scientist", "Descartes")),
                    new UpdateOneModel<>(new Document("_id", "5"), new Document("$set", new Document("scientist", "Marie Curie"))),
                    new DeleteOneModel<>(new Document("_id", "2")));
        try {
            template.requestBody("direct:unorderedBulkWrite", bulkOperations, BulkWriteResult.class);
            fail("Bulk operation should throw Exception");
        } catch (CamelExecutionException e) {
            extractAndAssertCamelMongoDbException(e, "duplicate key error");
            // count = 1000 + 2 inserted + 1 deleted
            assertEquals(1001, testCollection.countDocuments());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:bulkWrite").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=bulkWrite");
                from("direct:unorderedBulkWrite").setHeader(MongoDbConstants.BULK_ORDERED).constant(false)
                    .to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=bulkWrite");
            }
        };
    }
}
