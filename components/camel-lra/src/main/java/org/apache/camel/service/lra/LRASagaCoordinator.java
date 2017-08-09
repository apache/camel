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
package org.apache.camel.service.lra;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.Exchange;
import org.apache.camel.saga.CamelSagaCoordinator;
import org.apache.camel.saga.CamelSagaStep;
import org.apache.camel.util.ObjectHelper;

/**
 *
 */
public class LRASagaCoordinator implements CamelSagaCoordinator {

    private LRASagaService sagaService;

    private URL lraURL;

    public LRASagaCoordinator(LRASagaService sagaService, URL lraURL) {
        this.sagaService = ObjectHelper.notNull(sagaService, "sagaService");
        this.lraURL = ObjectHelper.notNull(lraURL, "lraURL");
    }

    @Override
    public CompletableFuture<Void> beginStep(Exchange exchange, CamelSagaStep step) {
        LRASagaStep sagaStep = LRASagaStep.fromCamelSagaStep(step, exchange);
        return sagaService.getClient().join(this.lraURL, sagaStep);
    }

    @Override
    public CompletableFuture<Void> compensate() {
        return sagaService.getClient().compensate(this.lraURL);
    }

    @Override
    public CompletableFuture<Void> complete() {
        return sagaService.getClient().complete(this.lraURL);
    }

    @Override
    public String getId() {
        return this.lraURL.toString();
    }
}
