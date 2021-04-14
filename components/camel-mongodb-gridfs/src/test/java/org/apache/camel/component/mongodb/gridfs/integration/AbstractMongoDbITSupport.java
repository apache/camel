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
package org.apache.camel.component.mongodb.gridfs.integration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.mongodb.services.MongoDBService;
import org.apache.camel.test.infra.mongodb.services.MongoDBServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractMongoDbITSupport extends CamelTestSupport {
    @RegisterExtension
    public static MongoDBService service = MongoDBServiceFactory.createService();

    protected static final String FILE_NAME = "filename.for.db.txt";
    protected static final String FILE_DATA = "This is some stuff to go into the db";
    protected MongoClient mongo;
    protected GridFSBucket gridFSBucket;

    public String getBucket() {
        return this.getClass().getSimpleName();
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
        mongo = MongoClients.create(service.getReplicaSetUrl());
        gridFSBucket = GridFSBuckets.create(mongo.getDatabase("test"), getBucket());

        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("classpath:mongodb.test.properties");
        context.getRegistry().bind("test", gridFSBucket);
        context.getRegistry().bind("myDb", mongo);

        return context;
    }
}
