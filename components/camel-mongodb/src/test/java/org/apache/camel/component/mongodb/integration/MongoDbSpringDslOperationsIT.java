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
package org.apache.camel.component.mongodb.integration;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.infra.core.annotations.ContextProvider;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;

public class MongoDbSpringDslOperationsIT extends MongoDbOperationsIT {

    @ContextProvider
    protected static CamelContext createSpringCamelContext() {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("myDb", mongo);

        XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader(applicationContext);
        xmlReader.loadBeanDefinitions(new ClassPathResource("org/apache/camel/component/mongodb/mongoBasicOperationsTest.xml"));

        applicationContext.refresh();

        @SuppressWarnings("deprecation")
        CamelContext ctx = null;
        try {
            ctx = SpringCamelContext.springCamelContext(applicationContext, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return ctx;
    }
}
