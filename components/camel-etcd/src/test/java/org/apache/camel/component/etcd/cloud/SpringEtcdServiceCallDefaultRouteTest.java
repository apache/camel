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

package org.apache.camel.component.etcd.cloud;

import java.net.URI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mousio.etcd4j.EtcdClient;
import org.apache.camel.component.etcd.EtcdConfiguration;
import org.apache.camel.component.etcd.EtcdHelper;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringEtcdServiceCallDefaultRouteTest extends CamelSpringTestSupport {
    private static final ObjectMapper MAPPER = EtcdHelper.createObjectMapper();
    private static final EtcdConfiguration CONFIGURATION = new EtcdConfiguration();
    private static final EtcdClient CLIENT = new EtcdClient(URI.create("http://localhost:2379"));

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/etcd/cloud/SpringEtcdServiceCallDefaultRouteTest.xml");
    }

    // *************************************************************************
    // Setup / tear down
    // *************************************************************************

    @Override
    public void doPreSetup() throws Exception {
        JsonNode service1 = MAPPER.createObjectNode()
            .put("name", "http-service")
            .put("address", "127.0.0.1")
            .put("port", "9091");
        JsonNode service2 = MAPPER.createObjectNode()
            .put("name", "http-service")
            .put("address", "127.0.0.1")
            .put("port", "9092");

        CLIENT.put(CONFIGURATION.getServicePath() + "service-1", MAPPER.writeValueAsString(service1)).send().get();
        CLIENT.put(CONFIGURATION.getServicePath() + "service-2", MAPPER.writeValueAsString(service2)).send().get();

        super.doPreSetup();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        CLIENT.deleteDir(CONFIGURATION.getServicePath()).recursive().send().get();
    }

    // *************************************************************************
    // Test
    // *************************************************************************

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("9091", "9092");

        template.sendBody("direct:start", null);
        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
    }
}
