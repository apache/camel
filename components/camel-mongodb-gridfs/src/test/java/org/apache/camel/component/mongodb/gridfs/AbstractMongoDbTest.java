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
package org.apache.camel.component.mongodb.gridfs;

import com.mongodb.client.MongoClient;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import org.apache.camel.CamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

public abstract class AbstractMongoDbTest extends CamelTestSupport {

    protected static final String FILE_NAME = "filename.for.db.txt";
    protected static final String FILE_DATA = "This is some stuff to go into the db";
    protected static MongoDbContainer container;
    protected MongoClient mongo;
    protected GridFSBucket gridFSBucket;

    public String getBucket() {
        return this.getClass().getSimpleName();
    }

    @BeforeAll
    public static void doBeforeAll() {
        container = new MongoDbContainer();
        container.start();
    }

    @AfterAll
    public static void doAfterAll() {
        if (container != null) {
            container.stop();
        }
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        gridFSBucket.find().forEach(gridFSFile -> gridFSBucket.delete(gridFSFile.getId()));
        super.tearDown();
        mongo.close();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        mongo = container.createClient();
        gridFSBucket = GridFSBuckets.create(mongo.getDatabase("test"), getBucket());

        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("classpath:mongodb.test.properties");
        context.getRegistry().bind("test", gridFSBucket);
        context.getRegistry().bind("myDb", mongo);

        return context;
    }
}
