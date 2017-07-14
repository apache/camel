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
package org.apache.camel.component.mongodb.gridfs;


import com.mongodb.MongoClient;
import com.mongodb.gridfs.GridFS;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public abstract class AbstractMongoDbTest extends CamelTestSupport {

    protected MongoClient mongo;
    protected GridFS gridfs;

    protected ApplicationContext applicationContext;

    @SuppressWarnings("deprecation")
    @Override
    public void doPostSetup() {
        mongo = applicationContext.getBean(MongoClient.class);
        gridfs = new GridFS(mongo.getDB("test"), getBucket());
    }

    public String getBucket() {
        return this.getClass().getSimpleName();
    }
    
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mongo.close();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext = new AnnotationConfigApplicationContext(EmbedMongoConfiguration.class);
        CamelContext ctx = new SpringCamelContext(applicationContext);
        PropertiesComponent pc = new PropertiesComponent("classpath:mongodb.test.properties");
        ctx.addComponent("properties", pc);
        return ctx;
    }
}