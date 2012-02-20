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

import java.util.Calendar;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.spring.SpringCamelContext;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MongoDbTailableCursorConsumerTest extends AbstractMongoDbTest {
    
    private DBCollection cappedTestCollection;
    private String cappedTestCollectionName;
    
    @Test
    public void testThousandRecords() throws Exception {
        assertEquals(0, cappedTestCollection.count());
        MockEndpoint mock = getMockEndpoint("mock:test");
        mock.expectedMessageCount(1000);
       
        // create a capped collection with max = 1000
        cappedTestCollection = db.createCollection(cappedTestCollectionName, 
                BasicDBObjectBuilder.start().add("capped", true).add("size", 1000000000).add("max", 1000).get());
        
        for (int i = 0; i < 1000; i++) {
            cappedTestCollection.insert(BasicDBObjectBuilder.start("increasing", i).add("string", "value" + i).get(), WriteConcern.SAFE);
        }
        assertEquals(1000, cappedTestCollection.count());

        addTestRoutes();
        context.startRoute("tailableCursorConsumer1");
        Thread.sleep(1000);
        mock.assertIsSatisfied();
        context.stopRoute("tailableCursorConsumer1");

    }
    
    @Test
    public void testNoRecords() throws Exception {
        assertEquals(0, cappedTestCollection.count());
        MockEndpoint mock = getMockEndpoint("mock:test");
        mock.expectedMessageCount(0);
       
        // create a capped collection with max = 1000
        cappedTestCollection = db.createCollection(cappedTestCollectionName, 
                BasicDBObjectBuilder.start().add("capped", true).add("size", 1000000000).add("max", 1000).get());
        assertEquals(0, cappedTestCollection.count());

        addTestRoutes();
        context.startRoute("tailableCursorConsumer1");
        Thread.sleep(1000);
        mock.assertIsSatisfied();
        context.stopRoute("tailableCursorConsumer1");

    }
    
    @Test
    public void testMultipleBursts() throws Exception {
        assertEquals(0, cappedTestCollection.count());
        MockEndpoint mock = getMockEndpoint("mock:test");
        mock.expectedMessageCount(5000);
       
        // create a capped collection with max = 1000
        cappedTestCollection = db.createCollection(cappedTestCollectionName, 
                BasicDBObjectBuilder.start().add("capped", true).add("size", 1000000000).add("max", 1000).get());
        
        addTestRoutes();
        context.startRoute("tailableCursorConsumer1");
        
        // pump 5 bursts of 1000 records each with 500ms pause between burst and burst
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 5000; i++) {
                    if (i % 1000 == 0) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    cappedTestCollection.insert(BasicDBObjectBuilder.start("increasing", i).add("string", "value" + i).get(), WriteConcern.SAFE);
                }
                
            }
        });
        
        // start the data pumping
        t.start();
        // before we assert, wait for the data pumping to end
        t.join();
        
        mock.assertIsSatisfied();
        context.stopRoute("tailableCursorConsumer1");

    }
    
    @Test
    public void testHundredThousandRecords() throws Exception {
        assertEquals(0, cappedTestCollection.count());
        final MockEndpoint mock = getMockEndpoint("mock:test");
        mock.expectedMessageCount(1000);
       
        // create a capped collection with max = 1000
        cappedTestCollection = db.createCollection(cappedTestCollectionName, 
                BasicDBObjectBuilder.start().add("capped", true).add("size", 1000000000).add("max", 1000).get());
        
        addTestRoutes();
        context.startRoute("tailableCursorConsumer1");
        
        // continuous pump of 100000 records, asserting incrementally to reduce overhead on the mock endpoint
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 1; i <= 100000; i++) {
                    cappedTestCollection.insert(BasicDBObjectBuilder.start("increasing", i).add("string", "value" + i).get(), WriteConcern.SAFE);
                    
                    // incrementally assert, as the mock endpoint stores all messages and otherwise the test would be sluggish
                    if (i % 1000 == 0) {
                        try {
                            MongoDbTailableCursorConsumerTest.this.assertAndResetMockEndpoint(mock);
                        } catch (Exception e) {
                            return;
                        }
                    }       
                }
            }
        });
        
        // start the data pumping
        t.start();
        // before we stop the route, wait for the data pumping to end
        t.join();
        
        context.stopRoute("tailableCursorConsumer1");

    }
    
    @Test
    public void testPersistentTailTrack() throws Exception {
        assertEquals(0, cappedTestCollection.count());
        final MockEndpoint mock = getMockEndpoint("mock:test");
        
        // drop the tracking collection
        db.getCollection(MongoDbTailTrackingConfig.DEFAULT_COLLECTION).drop();
        // create a capped collection with max = 1000
        cappedTestCollection = db.createCollection(cappedTestCollectionName, 
                BasicDBObjectBuilder.start().add("capped", true).add("size", 1000000000).add("max", 1000).get());
        cappedTestCollection.ensureIndex("increasing");
        
        addTestRoutes();
        context.startRoute("tailableCursorConsumer2");
        
        mock.expectedMessageCount(300);
        // pump 300 records
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 1; i <= 300; i++) {
                    cappedTestCollection.insert(BasicDBObjectBuilder.start("increasing", i).add("string", "value" + i).get(), WriteConcern.SAFE);  
                }
            }
        });
        
        // start the data pumping
        t.start();
        // before we continue wait for the data pump to end
        t.join();
        mock.assertIsSatisfied();
        mock.reset();
        context.stopRoute("tailableCursorConsumer2");
        while (context.getRouteStatus("tailableCursorConsumer2") != ServiceStatus.Stopped) { }
        context.startRoute("tailableCursorConsumer2");
        
        // expect 300 messages and not 600
        mock.expectedMessageCount(300);
        // pump 300 records
        t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 301; i <= 600; i++) {
                    cappedTestCollection.insert(BasicDBObjectBuilder.start("increasing", i).add("string", "value" + i).get(), WriteConcern.SAFE);  
                }
            }
        });
        // start the data pumping
        t.start();
        // before we continue wait for the data pump to end
        t.join();
        mock.assertIsSatisfied();
        
        // check that the first message received in this second batch corresponds to increasing=301
        Object firstBody = mock.getExchanges().get(0).getIn().getBody();
        assertTrue(firstBody instanceof DBObject);
        assertEquals(301, ((DBObject) firstBody).get("increasing"));
        
        // check that the lastVal is persisted at the right time: check before and after stopping the route
        assertEquals(300, db.getCollection(MongoDbTailTrackingConfig.DEFAULT_COLLECTION).findOne(new BasicDBObject("persistentId", "darwin")).get("lastTrackingValue"));
        // stop the route and verify the last value has been updated
        context.stopRoute("tailableCursorConsumer2");
        while (context.getRouteStatus("tailableCursorConsumer2") != ServiceStatus.Stopped) { }
        assertEquals(600, db.getCollection(MongoDbTailTrackingConfig.DEFAULT_COLLECTION).findOne(new BasicDBObject("persistentId", "darwin")).get("lastTrackingValue"));

    }
    
    @Test
    public void testPersistentTailTrackIncreasingDateField() throws Exception {
        assertEquals(0, cappedTestCollection.count());
        final MockEndpoint mock = getMockEndpoint("mock:test");
        final Calendar startTimestamp = Calendar.getInstance();
        
        // get default tracking collection
        DBCollection trackingCol = db.getCollection(MongoDbTailTrackingConfig.DEFAULT_COLLECTION);
        trackingCol.drop();
        trackingCol = db.getCollection(MongoDbTailTrackingConfig.DEFAULT_COLLECTION);
        
        // create a capped collection with max = 1000
        cappedTestCollection = db.createCollection(cappedTestCollectionName, 
                BasicDBObjectBuilder.start().add("capped", true).add("size", 1000000000).add("max", 1000).get());
        
        addTestRoutes();
        context.startRoute("tailableCursorConsumer2");
        
        mock.expectedMessageCount(300);
        // pump 300 records
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 1; i <= 300; i++) {
                    Calendar c = (Calendar) (startTimestamp.clone());
                    c.add(Calendar.MINUTE, i);
                    cappedTestCollection.insert(BasicDBObjectBuilder.start("increasing", c.getTime()).add("string", "value" + i).get(), WriteConcern.SAFE);
                }
            }
        });
        
        // start the data pumping
        t.start();
        // before we continue wait for the data pump to end
        t.join();
        mock.assertIsSatisfied();
        mock.reset();
        // ensure that the persisted lastVal is startTimestamp + 300min
        Calendar cal300 = (Calendar) startTimestamp.clone();
        cal300.add(Calendar.MINUTE, 300);
        context.stopRoute("tailableCursorConsumer2");
        assertEquals(cal300.getTime(), trackingCol.findOne(new BasicDBObject("persistentId", "darwin")).get(MongoDbTailTrackingConfig.DEFAULT_FIELD));
        context.startRoute("tailableCursorConsumer2");
        
        // expect 300 messages and not 600
        mock.expectedMessageCount(300);
        // pump 300 records
        t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 301; i <= 600; i++) {
                    Calendar c = (Calendar) (startTimestamp.clone());
                    c.add(Calendar.MINUTE, i);
                    cappedTestCollection.insert(BasicDBObjectBuilder.start("increasing", c.getTime()).add("string", "value" + i).get(), WriteConcern.SAFE);
                }
            }
        });
        // start the data pumping
        t.start();
        // before we continue wait for the data pump to end
        t.join();
        mock.assertIsSatisfied();
        Object firstBody = mock.getExchanges().get(0).getIn().getBody();
        assertTrue(firstBody instanceof DBObject);
        Calendar cal301 = (Calendar) startTimestamp.clone();
        cal301.add(Calendar.MINUTE, 301);
        assertEquals(cal301.getTime(), ((DBObject) firstBody).get("increasing"));
        // check that the persisted lastVal after stopping the route is startTimestamp + 600min
        context.stopRoute("tailableCursorConsumer2");
        Calendar cal600 = (Calendar) startTimestamp.clone();
        cal600.add(Calendar.MINUTE, 600);
        assertEquals(cal600.getTime(), trackingCol.findOne(new BasicDBObject("persistentId", "darwin")).get(MongoDbTailTrackingConfig.DEFAULT_FIELD));
    }
    
    @Test
    public void testCustomTailTrackLocation() throws Exception {
        assertEquals(0, cappedTestCollection.count());
        final MockEndpoint mock = getMockEndpoint("mock:test");
        
        // get the custom tracking collection and drop it (tailTrackDb=einstein&tailTrackCollection=curie&tailTrackField=newton)
        DBCollection trackingCol = mongo.getDB("einstein").getCollection("curie");
        trackingCol.drop();
        trackingCol = mongo.getDB("einstein").getCollection("curie");
        
        // create a capped collection with max = 1000
        cappedTestCollection = db.createCollection(cappedTestCollectionName, 
                BasicDBObjectBuilder.start().add("capped", true).add("size", 1000000000).add("max", 1000).get());
        
        addTestRoutes();
        context.startRoute("tailableCursorConsumer3");
        
        mock.expectedMessageCount(300);
        // pump 300 records
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 1; i <= 300; i++) {
                    cappedTestCollection.insert(BasicDBObjectBuilder.start("increasing", i).add("string", "value" + i).get(), WriteConcern.SAFE);  
                }
            }
        });
        
        // start the data pumping
        t.start();
        // before we continue wait for the data pump to end
        t.join();
        mock.assertIsSatisfied();
        mock.reset();
        
        // stop the route to ensure that our lastVal is persisted, and check it
        context.stopRoute("tailableCursorConsumer3");
        // ensure that the persisted lastVal is 300, newton is the name of the trackingField we are using
        assertEquals(300, trackingCol.findOne(new BasicDBObject("persistentId", "darwin")).get("newton"));
        context.startRoute("tailableCursorConsumer3");
        
        // expect 300 messages and not 600
        mock.expectedMessageCount(300);
        // pump 300 records
        t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 301; i <= 600; i++) {
                    cappedTestCollection.insert(BasicDBObjectBuilder.start("increasing", i).add("string", "value" + i).get(), WriteConcern.SAFE);  
                }
            }
        });
        // start the data pumping
        t.start();
        // before we continue wait for the data pump to end
        t.join();
        mock.assertIsSatisfied();
        // check that the first received body contains increasing=301 and not increasing=1, i.e. it's not starting from the top
        Object firstBody = mock.getExchanges().get(0).getIn().getBody();
        assertTrue(firstBody instanceof DBObject);
        assertEquals(301, ((DBObject) firstBody).get("increasing"));
        // check that the persisted lastVal after stopping the route is 600, newton is the name of the trackingField we are using
        context.stopRoute("tailableCursorConsumer3");
        assertEquals(600, trackingCol.findOne(new BasicDBObject("persistentId", "darwin")).get("newton"));

    }
    
    public void assertAndResetMockEndpoint(MockEndpoint mock) throws Exception {
        mock.assertIsSatisfied();
        mock.reset();
    }
    
    @Override
    public void initTestCase() {
        super.initTestCase();
        // drop the capped collection and let each test create what they need
        cappedTestCollectionName = properties.getProperty("mongodb.cappedTestCollection");
        cappedTestCollection = db.getCollection(cappedTestCollectionName);
        cappedTestCollection.drop();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/component/mongodb/mongoComponentTest.xml");
        return SpringCamelContext.springCamelContext(applicationContext);
    }
    
    protected void addTestRoutes() throws Exception {
        context.addRoutes(new RouteBuilder() {
            
            @Override
            public void configure() throws Exception {
                PropertiesComponent pc = new PropertiesComponent("classpath:mongodb.test.properties");
                context.addComponent("properties", pc);
                
                from("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.cappedTestCollection}}&tailTrackIncreasingField=increasing")
                    .id("tailableCursorConsumer1")
                    .autoStartup(false)
                    .to("mock:test");
                
                from("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.cappedTestCollection}}&tailTrackIncreasingField=increasing&persistentTailTracking=true&persistentId=darwin")
                    .id("tailableCursorConsumer2")
                    .autoStartup(false)
                    .to("mock:test");
                
                from("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.cappedTestCollection}}&tailTrackIncreasingField=increasing&" 
                     + "persistentTailTracking=true&persistentId=darwin&tailTrackDb=einstein&tailTrackCollection=curie&tailTrackField=newton")
                    .id("tailableCursorConsumer3")
                    .autoStartup(false)
                    .to("mock:test");
                
            }
        });
    }
    
}
