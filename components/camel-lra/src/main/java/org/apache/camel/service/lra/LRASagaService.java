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
package org.apache.camel.service.lra;

import java.net.URL;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.saga.CamelSagaCoordinator;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.saga.CamelSagaStep;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.service.ServiceSupport;

/**
 * A Camel saga service based on Microprofile LRA (https://github.com/eclipse/microprofile-lra).
 */
@JdkService("lra-saga-service")
@Configurer
@ManagedResource(description = "Managed LRASagaService")
public class LRASagaService extends ServiceSupport implements StaticService, CamelSagaService {

    private CamelContext camelContext;
    private ScheduledExecutorService executorService;
    private LRAClient client;
    private LRASagaRoutes routes;
    private final Set<String> sagaURIs = ConcurrentHashMap.newKeySet();

    // we want to be able to configure these following options
    @Metadata
    private String coordinatorUrl;
    @Metadata
    private String coordinatorContextPath = LRAConstants.DEFAULT_COORDINATOR_CONTEXT_PATH;
    @Metadata
    private String localParticipantUrl;
    @Metadata
    private String localParticipantContextPath = LRAConstants.DEFAULT_LOCAL_PARTICIPANT_CONTEXT_PATH;

    public LRASagaService() {
    }

    @Override
    public CompletableFuture<CamelSagaCoordinator> newSaga() {
        return client.newLRA()
                .thenApply(url -> new LRASagaCoordinator(LRASagaService.this, url));
    }

    @Override
    public CompletableFuture<CamelSagaCoordinator> getSaga(String id) {
        CompletableFuture<CamelSagaCoordinator> coordinator = new CompletableFuture<>();
        try {
            coordinator.complete(new LRASagaCoordinator(this, new URL(id)));
        } catch (Exception ex) {
            coordinator.completeExceptionally(ex);
        }
        return coordinator;
    }

    @Override
    public void registerStep(CamelSagaStep step) {
        // Register which uris should be exposed
        step.getCompensation().map(Endpoint::getEndpointUri).map(this.sagaURIs::add);
        step.getCompletion().map(Endpoint::getEndpointUri).map(this.sagaURIs::add);
    }

    @Override
    protected void doStart() throws Exception {
        if (this.executorService == null) {
            this.executorService = camelContext.getExecutorServiceManager()
                    .newDefaultScheduledThreadPool(this, "saga-lra");
        }
        if (this.client == null) {
            this.client = createLRAClient();
        }
    }

    /**
     * Use this method to override some behavior within the LRAClient
     *
     * @return the LRAClient to be used within the LRASagaService
     *
     */
    protected LRAClient createLRAClient() {
        return new LRAClient(this);
    }

    @Override
    protected void doStop() throws Exception {
        if (this.executorService != null) {
            camelContext.getExecutorServiceManager().shutdownGraceful(this.executorService);
            this.executorService = null;
        }
        if (this.client != null) {
            this.client.close();
            this.client = null;
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        if (this.routes == null) {
            this.routes = new LRASagaRoutes(this);
            try {
                this.camelContext.addRoutes(this.routes);
            } catch (Exception ex) {
                throw RuntimeCamelException.wrapRuntimeException(ex);
            }
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    public ScheduledExecutorService getExecutorService() {
        return executorService;
    }

    public LRAClient getClient() {
        return client;
    }

    @ManagedAttribute(description = "Coordinator URL")
    public String getCoordinatorUrl() {
        return coordinatorUrl;
    }

    public void setCoordinatorUrl(String coordinatorUrl) {
        this.coordinatorUrl = coordinatorUrl;
    }

    @ManagedAttribute(description = "Coordinator context-path")
    public String getCoordinatorContextPath() {
        return coordinatorContextPath;
    }

    public void setCoordinatorContextPath(String coordinatorContextPath) {
        this.coordinatorContextPath = coordinatorContextPath;
    }

    @ManagedAttribute(description = "Local participant URL")
    public String getLocalParticipantUrl() {
        return localParticipantUrl;
    }

    public void setLocalParticipantUrl(String localParticipantUrl) {
        this.localParticipantUrl = localParticipantUrl;
    }

    @ManagedAttribute(description = "Local participant context-path")
    public String getLocalParticipantContextPath() {
        return localParticipantContextPath;
    }

    public void setLocalParticipantContextPath(String localParticipantContextPath) {
        this.localParticipantContextPath = localParticipantContextPath;
    }

    public Set<String> getRegisteredURIs() {
        return sagaURIs;
    }

    @Override
    public String toString() {
        return "lra-saga-service";
    }
}
