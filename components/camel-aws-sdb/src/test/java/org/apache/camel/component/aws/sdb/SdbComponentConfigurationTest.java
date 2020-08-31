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
package org.apache.camel.component.aws.sdb;

import com.amazonaws.Protocol;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SdbComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        AmazonSDBClientMock mock = new AmazonSDBClientMock();

        context.getRegistry().bind("amazonSDBClient", mock);

        SdbComponent component = context.getComponent("aws-sdb", SdbComponent.class);
        SdbEndpoint endpoint = (SdbEndpoint) component.createEndpoint(
                "aws-sdb://TestDomain?amazonSDBClient=#amazonSDBClient&accessKey=xxx&secretKey=yyy");

        assertEquals("TestDomain", endpoint.getConfiguration().getDomainName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonSDBClient());
        assertEquals(SdbOperations.PutAttributes, endpoint.getConfiguration().getOperation());
        assertFalse(endpoint.getConfiguration().isConsistentRead());
        assertNull(endpoint.getConfiguration().getMaxNumberOfDomains());
    }

    @Test
    public void createEndpointWithOnlyAccessKeyAndSecretKey() throws Exception {
        SdbComponent component = context.getComponent("aws-sdb", SdbComponent.class);
        SdbEndpoint endpoint = (SdbEndpoint) component.createEndpoint(
                "aws-sdb://TestDomain?accessKey=xxx&secretKey=yyy");

        assertEquals("TestDomain", endpoint.getConfiguration().getDomainName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonSDBClient());
        assertEquals(SdbOperations.PutAttributes, endpoint.getConfiguration().getOperation());
        assertFalse(endpoint.getConfiguration().isConsistentRead());
        assertNull(endpoint.getConfiguration().getMaxNumberOfDomains());
    }

    @Test
    public void createEndpointWithMinimalConfigurationAndProvidedClient() throws Exception {
        AmazonSDBClientMock mock = new AmazonSDBClientMock();

        context.getRegistry().bind("amazonSDBClient", mock);

        SdbComponent component = context.getComponent("aws-sdb", SdbComponent.class);
        SdbEndpoint endpoint = (SdbEndpoint) component.createEndpoint("aws-sdb://TestDomain?"
                                                                      + "amazonSDBClient=#amazonSDBClient");

        assertEquals("TestDomain", endpoint.getConfiguration().getDomainName());
        assertNull(endpoint.getConfiguration().getAccessKey());
        assertNull(endpoint.getConfiguration().getSecretKey());
        assertSame(mock, endpoint.getConfiguration().getAmazonSDBClient());
        assertEquals(SdbOperations.PutAttributes, endpoint.getConfiguration().getOperation());
        assertFalse(endpoint.getConfiguration().isConsistentRead());
        assertNull(endpoint.getConfiguration().getMaxNumberOfDomains());
    }

    @Test
    public void createEndpointWithMaximalConfiguration() throws Exception {
        AmazonSDBClientMock mock = new AmazonSDBClientMock();

        context.getRegistry().bind("amazonSDBClient", mock);

        SdbComponent component = context.getComponent("aws-sdb", SdbComponent.class);
        SdbEndpoint endpoint = (SdbEndpoint) component.createEndpoint(
                "aws-sdb://TestDomain?amazonSDBClient=#amazonSDBClient&accessKey=xxx&secretKey=yyy&operation=DeleteAttributes&consistentRead=true"
                                                                      + "&maxNumberOfDomains=5");

        assertEquals("TestDomain", endpoint.getConfiguration().getDomainName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonSDBClient());
        assertEquals(SdbOperations.DeleteAttributes, endpoint.getConfiguration().getOperation());
        assertTrue(endpoint.getConfiguration().isConsistentRead());
        assertEquals(Integer.valueOf(5), endpoint.getConfiguration().getMaxNumberOfDomains());
    }

    @Test
    public void createEndpointWithoutDomainName() throws Exception {
        SdbComponent component = context.getComponent("aws-sdb", SdbComponent.class);
        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("aws-sdb:// "));
    }

    @Test
    public void createEndpointWithoutAmazonSDBClientConfiguration() throws Exception {
        SdbComponent component = context.getComponent("aws-sdb", SdbComponent.class);
        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("aws-sdb://TestDomain"));
    }

    @Test
    public void createEndpointWithoutAccessKeyConfiguration() throws Exception {
        SdbComponent component = context.getComponent("aws-sdb", SdbComponent.class);
        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("aws-sdb://TestDomain?secretKey=yyy"));
    }

    @Test
    public void createEndpointWithoutSecretKeyConfiguration() throws Exception {
        SdbComponent component = context.getComponent("aws-sdb", SdbComponent.class);
        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("aws-sdb://TestDomain?accessKey=xxx"));
    }

    @Test
    public void createEndpointWithoutSecretKeyAndAccessKeyConfiguration() throws Exception {
        AmazonSDBClientMock mock = new AmazonSDBClientMock();

        context.getRegistry().bind("amazonSDBClient", mock);

        SdbComponent component = context.getComponent("aws-sdb", SdbComponent.class);
        component.createEndpoint("aws-sdb://TestDomain?amazonSDBClient=#amazonSDBClient");
    }

    @Test
    public void createEndpointWithComponentEndpointElementsAndProxy() throws Exception {
        AmazonSDBClientMock mock = new AmazonSDBClientMock();

        context.getRegistry().bind("amazonSDBClient", mock);
        SdbComponent component = context.getComponent("aws-sdb", SdbComponent.class);
        SdbEndpoint endpoint = (SdbEndpoint) component
                .createEndpoint(
                        "aws-sdb://TestDomain?amazonSDBClient=#amazonSDBClient&accessKey=xxx&secretKey=yyy&region=US_EAST_1&operation=DeleteAttributes&consistentRead=true"
                                + "&maxNumberOfDomains=5&proxyHost=localhost&proxyPort=9000&proxyProtocol=HTTP");

        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
        assertEquals(Protocol.HTTP, endpoint.getConfiguration().getProxyProtocol());
        assertEquals("localhost", endpoint.getConfiguration().getProxyHost());
        assertEquals(Integer.valueOf(9000), endpoint.getConfiguration().getProxyPort());
    }
}
