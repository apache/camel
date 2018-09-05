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
package org.apache.camel.component.saga;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

/**
 * The saga component provides access to advanced options for managing the flow in the Saga EIP.
 */
@UriEndpoint(firstVersion = "2.21.0", scheme = "saga", title = "Saga", syntax = "saga:action", producerOnly = true, label = "core,endpoint")
public class SagaEndpoint extends DefaultEndpoint {

    public enum SagaEndpointAction {
        COMPLETE,
        COMPENSATE
    }

    @UriPath(description = "Action to execute (complete or compensate)")
    @Metadata(required = "true")
    private final SagaEndpointAction action;

    public SagaEndpoint(String endpointUri, SagaComponent component, String action) {
        super(endpointUri, component);
        this.action = SagaEndpointAction.valueOf(ObjectHelper.notNull(action, "action").toUpperCase());
    }

    @Override
    public Producer createProducer() throws Exception {
        if (SagaEndpointAction.COMPLETE.equals(this.action)) {
            return new SagaProducer(this, true);
        } else if (SagaEndpointAction.COMPENSATE.equals(this.action)) {
            return new SagaProducer(this, false);
        } else {
            throw new IllegalStateException("Unsupported action '" + this.action + "' in saga endpoint");
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not allowed for saga endpoint");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
