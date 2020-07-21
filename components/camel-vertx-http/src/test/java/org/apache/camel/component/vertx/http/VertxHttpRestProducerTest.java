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
package org.apache.camel.component.vertx.http;

import java.net.URI;

import org.apache.camel.CamelContext;
import org.apache.camel.component.rest.openapi.RestOpenApiComponent;
import org.junit.jupiter.api.Test;

public class VertxHttpRestProducerTest extends VertxHttpTestSupport {

    @Test
    public void testVertxHttpRestProducer() throws InterruptedException {
        template.requestBodyAndHeader("petstore:getPetById", null, "petId", 1);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        RestOpenApiComponent petstore = new RestOpenApiComponent(camelContext);
        petstore.setHost("https://petstore3.swagger.io");
        petstore.setSpecificationUri(new URI("https://petstore3.swagger.io/api/v3/openapi.json"));
        petstore.setComponentName("vertx-http");
        camelContext.addComponent("petstore", petstore);
        return camelContext;
    }
}
