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
package org.apache.camel.component.direct;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Represents a direct endpoint that synchronously invokes the consumers of the
 * endpoint when a producer sends a message to it.
 *
 * @version $Revision$
 */
public class DirectEndpoint<E extends Exchange> extends DefaultEndpoint<E> {
    private static final transient Log LOG = LogFactory.getLog(DirectEndpoint.class);
    private boolean allowMultipleConsumers = true;
    private final CopyOnWriteArrayList<DefaultConsumer<E>> consumers = new CopyOnWriteArrayList<DefaultConsumer<E>>();

    public DirectEndpoint(String uri, DirectComponent<E> component) {
        super(uri, component);
    }

    public DirectEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        return new DirectProducer<E>(this);
    }

    public Consumer<E> createConsumer(Processor processor) throws Exception {
        return new DefaultConsumer<E>(this, processor) {
            @Override
            public void start() throws Exception {
                if (!allowMultipleConsumers && !consumers.isEmpty()) {
                    throw new IllegalStateException("Endpoint " + getEndpointUri() + " only allows 1 active consumer but you attempted to start a 2nd consumer.");
                }

                consumers.add(this);
                super.start();
            }

            @Override
            public void stop() throws Exception {
                super.stop();
                consumers.remove(this);
            }
        };
    }

    public boolean isAllowMultipleConsumers() {
        return allowMultipleConsumers;
    }

    public void setAllowMultipleConsumers(boolean allowMutlipleConsumers) {
        this.allowMultipleConsumers = allowMutlipleConsumers;
    }

    public boolean isSingleton() {
        return true;
    }

    public List<DefaultConsumer<E>> getConsumers() {
        return consumers;
    }
}
