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
package org.apache.camel.component.dapr.operations;

import io.dapr.client.DaprPreviewClient;
import io.dapr.client.domain.LockRequest;
import io.dapr.client.domain.UnlockRequest;
import io.dapr.client.domain.UnlockResponseStatus;
import org.apache.camel.Exchange;
import org.apache.camel.component.dapr.DaprConfiguration;
import org.apache.camel.component.dapr.DaprConfigurationOptionsProxy;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.component.dapr.DaprOperation;
import org.apache.camel.component.dapr.LockOperation;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DaprLockTest extends CamelTestSupport {

    @Mock
    private DaprPreviewClient client;
    @Mock
    private DaprEndpoint endpoint;

    @Test
    @SuppressWarnings("unchecked")
    void testTryLock() throws Exception {
        Boolean mockResult = Boolean.TRUE;

        when(endpoint.getPreviewClient()).thenReturn(client);
        when(client.tryLock(any(LockRequest.class))).thenReturn(Mono.just(mockResult));

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.lock);
        configuration.setStoreName("myStore");
        configuration.setResourceId("myResouce");
        configuration.setLockOwner("me");
        configuration.setExpiryInSeconds(100);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        DaprLockHandler operation = new DaprLockHandler(configurationOptionsProxy, endpoint);
        DaprOperationResponse response = operation.handle(exchange);
        Boolean lockResult = (Boolean) response.getBody();

        assertNotNull(response);
        assertNotNull(lockResult);
        assertEquals(mockResult, lockResult);
    }

    @Test
    void testTryLockConfiguration() throws Exception {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.lock);
        configuration.setLockOperation(LockOperation.tryLock);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: storeName, resourceId, lockOwner and expiryInSeconds empty
        final DaprLockHandler operation = new DaprLockHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: resourceId, lockOwner and expiryInSeconds empty
        configuration.setStoreName("myStore");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: lockOwner and expiryInSeconds empty
        configuration.setResourceId("myResource");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 4: expiryInSeconds empty
        configuration.setLockOwner("me");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 5: valid configuration
        configuration.setExpiryInSeconds(100);
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    void testUnlock() throws Exception {
        UnlockResponseStatus mockResult = UnlockResponseStatus.SUCCESS;

        when(endpoint.getPreviewClient()).thenReturn(client);
        when(client.unlock(any(UnlockRequest.class))).thenReturn(Mono.just(mockResult));

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.lock);
        configuration.setLockOperation(LockOperation.unlock);
        configuration.setStoreName("myStore");
        configuration.setResourceId("myResouce");
        configuration.setLockOwner("me");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        DaprLockHandler operation = new DaprLockHandler(configurationOptionsProxy, endpoint);
        DaprOperationResponse response = operation.handle(exchange);
        UnlockResponseStatus unlockResponse = (UnlockResponseStatus) response.getBody();

        assertNotNull(response);
        assertNotNull(unlockResponse);
        assertEquals(mockResult, unlockResponse);
    }

    @Test
    void testUnlockConfiguration() throws Exception {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.lock);
        configuration.setLockOperation(LockOperation.unlock);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: storeName, resourceId and lockOwner empty
        final DaprLockHandler operation = new DaprLockHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: resourceId and empty
        configuration.setStoreName("myStore");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: lockOwner empty
        configuration.setResourceId("myResource");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 4: valid configuration
        configuration.setLockOwner("me");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }
}
