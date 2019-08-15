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

import java.util.Properties;

import com.mongodb.MongoClient;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.spring.SpringCamelContext;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class MongoDbConnectionBeansTest extends AbstractMongoDbTest {

    @Test
    public void checkConnectionFromProperties() {
        MongoDbEndpoint testEndpoint = context.getEndpoint(
                "mongodb:anyName?mongoConnection=#myDb&database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=count&dynamicity=true",
                MongoDbEndpoint.class);
        assertNotEquals("myDb", testEndpoint.getConnectionBean());
        assertEquals(mongo, testEndpoint.getMongoConnection());
    }

    @Test
    public void checkConnectionFromBean() {
        MongoDbEndpoint testEndpoint = context.getEndpoint(
                "mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=count&dynamicity=true",
                MongoDbEndpoint.class);
        assertEquals("myDb", testEndpoint.getConnectionBean());
        assertEquals(mongo, testEndpoint.getMongoConnection());
    }

    @Test
    public void checkConnectionBothExisting() {
        MongoDbEndpoint testEndpoint = context.getEndpoint(
                "mongodb:myDb?mongoConnection=#myDbS&database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=count&dynamicity=true",
                MongoDbEndpoint.class);
        assertEquals("myDb", testEndpoint.getConnectionBean());
        MongoClient myDbS = applicationContext.getBean("myDbS", MongoClient.class);
        assertEquals(myDbS, testEndpoint.getMongoConnection());
    }

    @Test(expected = Exception.class)
    public void checkMissingConnection() {
        MongoDbEndpoint testEndpoint = context.getEndpoint(
                "mongodb:anythingNotRelated?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=count&dynamicity=true",
                MongoDbEndpoint.class);
    }

    @Test
    public void checkConnectionOnComponent() throws Exception {
        MongoDbComponent component = context.getComponent("mongodb", MongoDbComponent.class);
        MongoClient myDbS = applicationContext.getBean("myDbS", MongoClient.class);
        component.setMongoConnection(myDbS);
        Endpoint endpoint = component.createEndpoint("mongodb:justARouteName?database={{mongodb.testDb}}&collection="
                + "{{mongodb.testCollection}}&operation=count&dynamicity=true");
        assertIsInstanceOf(MongoDbEndpoint.class, endpoint);
        assertEquals(myDbS, ((MongoDbEndpoint) endpoint).getMongoConnection());
    }

}
