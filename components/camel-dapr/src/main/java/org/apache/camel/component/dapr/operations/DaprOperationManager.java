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

import java.util.EnumMap;
import java.util.Map;

import io.dapr.client.DaprClient;
import org.apache.camel.Exchange;
import org.apache.camel.component.dapr.DaprConfigurationOptionsProxy;
import org.apache.camel.component.dapr.DaprOperation;

public class DaprOperationManager {
    private final DaprConfigurationOptionsProxy configurationOptionsProxy;
    private final Map<DaprOperation, DaprOperationHandler> handlerMap = new EnumMap<>(DaprOperation.class);

    public DaprOperationManager(DaprConfigurationOptionsProxy configurationOptionsProxy) {
        this.configurationOptionsProxy = configurationOptionsProxy;
        handlerMap.put(DaprOperation.invokeService, new DaprServiceInvocationHandler(configurationOptionsProxy));
    }

    public DaprOperationResponse process(Exchange exchange, DaprClient client) throws Exception {
        DaprOperation operation = configurationOptionsProxy.getOperation();
        DaprOperationHandler handler = handlerMap.get(operation);

        if (handler == null) {
            throw new UnsupportedOperationException("No handler for operation " + operation);
        }

        handler.validateConfiguration(exchange);

        return handler.handle(exchange, client);
    }
}
