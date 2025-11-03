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
import org.apache.camel.component.dapr.DaprConfigurationOptionsProxy;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.component.dapr.LockOperation;
import org.apache.camel.util.ObjectHelper;

public class DaprLockHandler implements DaprOperationHandler {

    private final DaprConfigurationOptionsProxy configurationOptionsProxy;
    private final DaprEndpoint endpoint;

    public DaprLockHandler(DaprConfigurationOptionsProxy configurationOptionsProxy, DaprEndpoint endpoint) {
        this.configurationOptionsProxy = configurationOptionsProxy;
        this.endpoint = endpoint;
    }

    @Override
    public DaprOperationResponse handle(Exchange exchange) {
        LockOperation lockOperation = configurationOptionsProxy.getLockOperation(exchange);
        DaprPreviewClient client = endpoint.getPreviewClient();

        switch (lockOperation) {
            case tryLock:
                return tryLock(exchange, client);
            case unlock:
                return unlock(exchange, client);
            default:
                throw new IllegalArgumentException("Unsupported lock operation");
        }
    }

    private DaprOperationResponse tryLock(Exchange exchange, DaprPreviewClient client) {
        String storeName = configurationOptionsProxy.getStoreName(exchange);
        String resourceId = configurationOptionsProxy.getResourceId(exchange);
        String lockOwner = configurationOptionsProxy.getLockOwner(exchange);
        Integer expiryInSeconds = configurationOptionsProxy.getExpiryInSeconds(exchange);

        LockRequest lockRequest = new LockRequest(storeName, resourceId, lockOwner, expiryInSeconds);

        Boolean response = client.tryLock(lockRequest).block();

        return DaprOperationResponse.create(response);
    }

    private DaprOperationResponse unlock(Exchange exchange, DaprPreviewClient client) {
        String storeName = configurationOptionsProxy.getStoreName(exchange);
        String resourceId = configurationOptionsProxy.getResourceId(exchange);
        String lockOwner = configurationOptionsProxy.getLockOwner(exchange);

        UnlockRequest unlockRequest = new UnlockRequest(storeName, resourceId, lockOwner);

        UnlockResponseStatus response = client.unlock(unlockRequest).block();

        return DaprOperationResponse.create(response);
    }

    @Override
    public void validateConfiguration(Exchange exchange) {
        LockOperation lockOperation = configurationOptionsProxy.getLockOperation(exchange);
        String storeName = configurationOptionsProxy.getStoreName(exchange);
        String resourceId = configurationOptionsProxy.getResourceId(exchange);
        String lockOwner = configurationOptionsProxy.getLockOwner(exchange);
        Integer expiryInSeconds = configurationOptionsProxy.getExpiryInSeconds(exchange);

        if (ObjectHelper.isEmpty(storeName) || ObjectHelper.isEmpty(resourceId) || ObjectHelper.isEmpty(lockOwner)) {
            throw new IllegalArgumentException("Store Name, Resource Id and Lock Owner must not be empty for lock operations");
        }

        if (LockOperation.tryLock.equals(lockOperation) && ObjectHelper.isEmpty(expiryInSeconds)) {
            throw new IllegalArgumentException("Expiry time must not be empty for 'tryLock' operation");
        }
    }

}
