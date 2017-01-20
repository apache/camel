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

import java.util.Formatter;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.util.JSON;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;



public abstract class AbstractMongoDbTest extends CamelTestSupport {

    protected static MongoClient mongo;
    protected static MongoDatabase db;
    protected static MongoCollection<BasicDBObject> testCollection;
    protected static MongoCollection<BasicDBObject> dynamicCollection;
    
    protected static String dbName = "test";
    protected static String testCollectionName;
    protected static String dynamicCollectionName;

    protected ApplicationContext applicationContext;

    @Override
    public void doPostSetup() {
        mongo = applicationContext.getBean(MongoClient.class);
        db = mongo.getDatabase(dbName);

        // Refresh the test collection - drop it and recreate it. We don't do this for the database because MongoDB would create large
        // store files each time
        testCollectionName = "camelTest";
        testCollection = db.getCollection(testCollectionName, BasicDBObject.class);
        testCollection.drop();
        testCollection = db.getCollection(testCollectionName, BasicDBObject.class);
        
        dynamicCollectionName = testCollectionName.concat("Dynamic");
        dynamicCollection = db.getCollection(dynamicCollectionName, BasicDBObject.class);
        dynamicCollection.drop();
        dynamicCollection = db.getCollection(dynamicCollectionName, BasicDBObject.class);

    }

    @Override
    public void tearDown() throws Exception {
        testCollection.drop();
        dynamicCollection.drop();

        super.tearDown();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext = new AnnotationConfigApplicationContext(EmbedMongoConfiguration.class);
        CamelContext ctx = SpringCamelContext.springCamelContext(applicationContext);
        PropertiesComponent pc = new PropertiesComponent("classpath:mongodb.test.properties");
        ctx.addComponent("properties", pc);
        return ctx;
    }

    protected void pumpDataIntoTestCollection() {
        // there should be 100 of each
        String[] scientists = {"Einstein", "Darwin", "Copernicus", "Pasteur", "Curie", "Faraday", "Newton", "Bohr", "Galilei", "Maxwell"};
        for (int i = 1; i <= 1000; i++) {
            int index = i % scientists.length;
            Formatter f = new Formatter();
            String doc = f.format("{\"_id\":\"%d\", \"scientist\":\"%s\", \"fixedField\": \"fixedValue\"}", i, scientists[index]).toString();
            IOHelper.close(f);
            testCollection.insertOne((BasicDBObject) JSON.parse(doc));
        }
        assertEquals("Data pumping of 1000 entries did not complete entirely", 1000L, testCollection.count());
    }

    protected CamelMongoDbException extractAndAssertCamelMongoDbException(Object result, String message) {
        assertTrue("Result is not an Exception", result instanceof Throwable);
        assertTrue("Result is not an CamelExecutionException", result instanceof CamelExecutionException);
        Throwable exc = ((CamelExecutionException) result).getCause();
        assertTrue("Result is not an CamelMongoDbException", exc instanceof CamelMongoDbException);
        CamelMongoDbException camelExc = ObjectHelper.cast(CamelMongoDbException.class, exc);
        if (message != null) {
            assertTrue("CamelMongoDbException doesn't contain desired message string", camelExc.getMessage().contains(message));
        }
        return camelExc;
    }

}