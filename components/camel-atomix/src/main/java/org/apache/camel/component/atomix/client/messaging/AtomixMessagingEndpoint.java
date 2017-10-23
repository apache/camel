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
package org.apache.camel.component.atomix.client.messaging;


import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.atomix.client.AbstractAtomixClientEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * The atomix-messaging component is used to access Atomix's <a href="http://atomix.io/atomix/docs/groups/#messaging">group messaging</a>.
 */
@UriEndpoint(
    firstVersion = "2.20.0",
    scheme = "atomix-messaging",
    title = "Atomix Messaging",
    syntax = "atomix-messaging:resourceName",
    consumerClass = AtomixMessagingConsumer.class,
    label = "clustering")
final class AtomixMessagingEndpoint extends AbstractAtomixClientEndpoint<AtomixMessagingComponent, AtomixMessagingConfiguration> {
    @UriParam
    private AtomixMessagingConfiguration configuration;

    AtomixMessagingEndpoint(String uri, AtomixMessagingComponent component, String resourceName) {
        super(uri, component, resourceName);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new AtomixMessagingProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new AtomixMessagingConsumer(this, processor);
    }

    @Override
    public AtomixMessagingConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    public void setConfiguration(AtomixMessagingConfiguration configuration) {
        this.configuration = configuration;
    }
}

