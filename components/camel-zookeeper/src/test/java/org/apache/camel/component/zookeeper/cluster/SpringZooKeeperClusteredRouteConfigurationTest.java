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
package org.apache.camel.component.zookeeper.cluster;

import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.impl.cluster.ClusteredRoutePolicyFactory;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringZooKeeperClusteredRouteConfigurationTest extends CamelSpringTestSupport {

    @Test
    public void test() {
        assertNotNull(context.hasService(CamelClusterService.class));
        assertTrue(context.getRoutePolicyFactories().stream().anyMatch(ClusteredRoutePolicyFactory.class::isInstance));
    }

    // ***********************
    // Routes
    // ***********************

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/zookeeper/cluster/SpringZooKeeperClusteredRouteConfigurationTest.xml");
    }
}
