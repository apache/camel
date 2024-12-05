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
package org.apache.camel.component.consul.cluster;

import java.util.Optional;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.consul.services.ConsulService;
import org.apache.camel.test.infra.consul.services.ConsulServiceFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ConsulClusterViewIT {
    @RegisterExtension
    public static ConsulService service = ConsulServiceFactory.createService();

    @Test
    public void getLeaderTest() throws Exception {
        //Set up a single node cluster.
        ConsulClusterService consulClusterService = new ConsulClusterService();
        consulClusterService.setId("node");
        consulClusterService.setUrl(service.getConsulUrl());
        consulClusterService.setRootPath("root");

        //Set up context with single locked route.
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().setName("context");
        context.addService(consulClusterService);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("master:key:timer:consul?repeatCount=1")
                        .routeId("route1")
                        .stop();
            }
        });

        context.start();

        //Get view and leader.
        CamelClusterView view = consulClusterService.getView("key");
        Optional<CamelClusterMember> leaderOptional = view.getLeader();

        Assertions.assertTrue(leaderOptional.isPresent());
        Assertions.assertTrue(leaderOptional.get().isLeader());
        Assertions.assertTrue(leaderOptional.get().isLocal());

        context.stop();
    }

}
