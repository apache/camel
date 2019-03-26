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
package org.apache.camel.component.mllp;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit.rule.mllp.MllpJUnitResourceException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests related to maxConcurrentConsumers configuration
 */
public class MllpMaxConcurrentConsumersTest extends CamelTestSupport {

    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();

    @Rule
    public MllpClientResource mllpClient2 = new MllpClientResource();

    @EndpointInject(value = "mock://result")
    MockEndpoint result;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected void doPreSetup() throws Exception {
        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        mllpClient2.setMllpHost("localhost");
        mllpClient2.setMllpPort(AvailablePortFinder.getNextAvailable());

        super.doPreSetup();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.setName(this.getClass().getSimpleName());

        return context;
    }

    @Test
    public void testConcurrentConsumersLessThanMaxConsumers() throws Exception {

        addTestRoute(2);
        result.expectedMessageCount(1);

        mllpClient.connect();

        String testMessage = "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20160902123950|RISTECH|ADT^A08|00001|D|2.3|||||||" + '\r' + '\n';
        mllpClient.sendMessageAndWaitForAcknowledgement(testMessage, 10000);

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

    @Test(expected = MllpJUnitResourceException.class)
    public void testConcurrentConsumersMoreThanMaxConsumers() throws Exception {
        addTestRoute(1);
        result.expectedMessageCount(1);

        mllpClient.connect();

        String testMessage = "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20160902123950|RISTECH|ADT^A08|00001|D|2.3|||||||" + '\r' + '\n';
        mllpClient.sendMessageAndWaitForAcknowledgement(testMessage, 10000);

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);

        // second connection should fail
        mllpClient2.connect();
    }

    void addTestRoute(int maxConcurrentConsumers) throws Exception {
        RouteBuilder builder = new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                String routeId = "mllp-max-concurrent-consumers-route";

                fromF("mllp://%s:%d?maxConcurrentConsumers=%d&autoAck=true&connectTimeout=100&receiveTimeout=1000",
                        mllpClient.getMllpHost(), mllpClient.getMllpPort(), maxConcurrentConsumers)
                        .routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Test route received message")
                        .to(result);

            }
        };
        context.addRoutes(builder);
        context.start();
    }
}

