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
package org.apache.camel.component.dynamicrouter.integration;

import java.util.Arrays;
import java.util.List;

import javax.management.MBeanFeatureInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DynamicRouterMBeansIT {

    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    static String SERVICE_NAME = "DynamicRouterControlService";

    CamelContext context;

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:subscribe").to("dynamic-router-control:subscribe");
            }
        });
    }

    @BeforeEach
    void setUp() {
        context = contextExtension.getContext();
    }

    MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    ObjectInstance getServiceMBean() throws Exception {
        MBeanServer mBeanServer = getMBeanServer();
        String serviceName = mBeanServer.queryNames(null, null).stream()
                .map(ObjectName::getCanonicalName)
                .filter(objName -> objName.contains(SERVICE_NAME))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Service MBean not found"));
        ObjectName controlServiceObjectName = ObjectName.getInstance(serviceName);
        return mBeanServer.getObjectInstance(controlServiceObjectName);
    }

    @Test
    void testControlServiceBeanExists() throws Exception {
        ObjectInstance objectInstance = getServiceMBean();
        assertNotNull(objectInstance);
    }

    @Test
    void testControlServiceBeanOperations() throws Exception {
        ObjectInstance objectInstance = getServiceMBean();
        MBeanServer mBeanServer = getMBeanServer();
        MBeanInfo mBeanInfo = mBeanServer.getMBeanInfo(objectInstance.getObjectName());
        boolean result = Arrays.stream(mBeanInfo.getOperations())
                .map(MBeanFeatureInfo::getName)
                .toList()
                .containsAll(
                        List.of(
                                "subscribeWithPredicateExpression",
                                "subscribeWithPredicateBean",
                                "subscribeWithPredicateInstance",
                                "removeSubscription"));
        assertTrue(result);
    }

    @Test
    void testControlServiceBeanAttributes() throws Exception {
        ObjectInstance objectInstance = getServiceMBean();
        MBeanServer mBeanServer = getMBeanServer();
        MBeanInfo mBeanInfo = mBeanServer.getMBeanInfo(objectInstance.getObjectName());
        boolean result = Arrays.stream(mBeanInfo.getAttributes())
                .map(MBeanFeatureInfo::getName)
                .toList()
                .containsAll(
                        List.of(
                                "SubscriptionsMap",
                                "SubscriptionsStatisticsMap"));
        assertTrue(result);
    }
}
