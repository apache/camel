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
package org.apache.camel.component.mongodb3;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.mongodb.MongoClient;

public class MongoDbOperationsWithConnectionPropertyTest extends MongoDbOperationsTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        applicationContext = new AnnotationConfigApplicationContext(EmbedMongoConfiguration.class);
        @SuppressWarnings("deprecation")
        CamelContext ctx = SpringCamelContext.springCamelContext(applicationContext, true);
        PropertiesComponent pc = new PropertiesComponent("classpath:mongodb.test.properties");
        MongoClient cli = (MongoClient) applicationContext.getBean("myDb");
        Properties initialProperties = pc.getInitialProperties();
        if ( initialProperties == null ) {
            initialProperties = new Properties();
            pc.setInitialProperties(initialProperties);
        }
        log.info("Setting connection bean {} as mongoConnection property.", cli);
        pc.getInitialProperties().put("mongoConnection", cli);
        ctx.addComponent("properties", pc);
        return ctx;
    }
}
