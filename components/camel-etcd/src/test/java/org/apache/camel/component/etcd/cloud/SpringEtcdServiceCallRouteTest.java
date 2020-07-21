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
package org.apache.camel.component.etcd.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import mousio.etcd4j.EtcdClient;
import org.apache.camel.component.etcd.support.SpringEtcdTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringEtcdServiceCallRouteTest extends SpringEtcdTestSupport {
    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/etcd/cloud/SpringEtcdServiceCallRouteTest.xml");
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
        JsonNode service3 = MAPPER.createObjectNode()
            .put("name", "http-service")
            .put("address", "127.0.0.1")
            .put("port", "9093");
        JsonNode service4 = MAPPER.createObjectNode()
            .put("name", "http-service")
            .put("address", "127.0.0.1")
            .put("port", "9094");

        EtcdClient client = getClient();
        client.put("/etcd-services-1/" + "service-1", MAPPER.writeValueAsString(service1)).send().get();
        client.put("/etcd-services-1/" + "service-2", MAPPER.writeValueAsString(service2)).send().get();
        client.put("/etcd-services-2/" + "service-3", MAPPER.writeValueAsString(service3)).send().get();
        client.put("/etcd-services-2/" + "service-4", MAPPER.writeValueAsString(service4)).send().get();

        super.doPreSetup();
    }

    @Override
    protected void cleanupResources() throws Exception {
        getClient().deleteDir("/etcd-services-1/").recursive().send().get();
        getClient().deleteDir("/etcd-services-2/").recursive().send().get();

        super.cleanupResources();
    }

    // *************************************************************************
    // Test
    // *************************************************************************

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:result-1").expectedMessageCount(2);
        getMockEndpoint("mock:result-1").expectedBodiesReceivedInAnyOrder("service-1 9091", "service-1 9092");
        getMockEndpoint("mock:result-2").expectedMessageCount(2);
        getMockEndpoint("mock:result-2").expectedBodiesReceivedInAnyOrder("service-2 9093", "service-2 9094");

        template.sendBody("direct:start", "service-1");
        template.sendBody("direct:start", "service-1");
        template.sendBody("direct:start", "service-2");
        template.sendBody("direct:start", "service-2");

        assertMockEndpointsSatisfied();
    }
}
