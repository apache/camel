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
package org.apache.camel.component.jclouds;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.BlobStoreContextFactory;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JcloudsSpringBlobstoreTest extends CamelSpringTestSupport {


    @EndpointInject(uri = "mock:result-foo")
    protected MockEndpoint resultFoo;


    @EndpointInject(uri = "mock:result-bar")
    protected MockEndpoint resultBar;

    @BeforeClass
    public static void setUpClass() throws Exception {
        BlobStoreContext context = new BlobStoreContextFactory().createContext("transient", "id", "credential");
        context.getBlobStore().createContainerInLocation(null, "foo");
        context.getBlobStore().createContainerInLocation(null, "bar");
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("classpath:blobstore-test.xml");
    }

    @Test
    public void testBlobStorePut() throws InterruptedException {
        resultFoo.expectedMessageCount(1);
        template.sendBody("direct:start", "Some message");
        resultFoo.assertIsSatisfied();
    }

    @Test
    public void testBlobStoreGet() throws InterruptedException {
        resultFoo.expectedMessageCount(1);
        template.sendBody("direct:start", "Some message");
        resultFoo.assertIsSatisfied();
    }

    @Test
    public void testProduceWithUrlParametes() throws InterruptedException {
        resultBar.expectedMessageCount(1);
        template.sendBody("direct:start-with-url-parameters", "Some message");
        resultBar.assertIsSatisfied();
    }
}
