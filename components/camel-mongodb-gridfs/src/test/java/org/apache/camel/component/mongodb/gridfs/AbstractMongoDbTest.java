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

import com.mongodb.MongoClient;
import com.mongodb.gridfs.GridFS;
import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;

import static org.apache.camel.component.mongodb.gridfs.EmbedMongoConfiguration.createMongoClient;

public abstract class AbstractMongoDbTest extends CamelTestSupport {

    protected MongoClient mongo;
    protected GridFS gridfs;

    public String getBucket() {
        return this.getClass().getSimpleName();
    }
    
    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mongo.close();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        mongo = createMongoClient();
        gridfs = new GridFS(mongo.getDB("test"), getBucket());

        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("classpath:mongodb.test.properties");
        context.getRegistry().bind("test", gridfs);
        context.getRegistry().bind("myDb", mongo);

        return context;
    }
}
