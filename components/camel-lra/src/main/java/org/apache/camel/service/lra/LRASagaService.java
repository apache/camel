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
import org.apache.camel.saga.CamelSagaCoordinator;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.saga.CamelSagaStep;
import org.apache.camel.support.service.ServiceSupport;

/**
 * A Camel saga service based on LRA (https://github.com/eclipse/microprofile-sandbox/tree/master/proposals/0009-LRA).
 */
public class LRASagaService extends ServiceSupport implements CamelSagaService {

    private CamelContext camelContext;

    private ScheduledExecutorService executorService;

    private LRAClient client;

    private LRASagaRoutes routes;

    private String coordinatorUrl;

    private String coordinatorContextPath = LRAConstants.DEFAULT_COORDINATOR_CONTEXT_PATH;

    private String localParticipantUrl;

    private String localParticipantContextPath = LRAConstants.DEFAULT_LOCAL_PARTICIPANT_CONTEXT_PATH;

    private Set<String> sagaURIs = ConcurrentHashMap.newKeySet();


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
            this.client = new LRAClient(this);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (this.executorService != null) {
            camelContext.getExecutorServiceManager().shutdownGraceful(this.executorService);
            this.executorService = null;
        }
        if (this.client != null) {
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
                throw new RuntimeCamelException(ex);
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

    public String getCoordinatorUrl() {
        return coordinatorUrl;
    }

    public void setCoordinatorUrl(String coordinatorUrl) {
        this.coordinatorUrl = coordinatorUrl;
    }

    public String getCoordinatorContextPath() {
        return coordinatorContextPath;
    }

    public void setCoordinatorContextPath(String coordinatorContextPath) {
        this.coordinatorContextPath = coordinatorContextPath;
    }

    public String getLocalParticipantUrl() {
        return localParticipantUrl;
    }

    public void setLocalParticipantUrl(String localParticipantUrl) {
        this.localParticipantUrl = localParticipantUrl;
    }

    public String getLocalParticipantContextPath() {
        return localParticipantContextPath;
    }

    public void setLocalParticipantContextPath(String localParticipantContextPath) {
        this.localParticipantContextPath = localParticipantContextPath;
    }

    public Set<String> getRegisteredURIs() {
        return sagaURIs;
    }

}
