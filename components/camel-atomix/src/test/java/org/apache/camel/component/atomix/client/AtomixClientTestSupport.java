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
package org.apache.camel.component.atomix.client;

import java.util.Map;

import io.atomix.AtomixClient;
import io.atomix.AtomixReplica;
import io.atomix.catalyst.transport.Address;
import org.apache.camel.Component;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;

public abstract class AtomixClientTestSupport extends CamelTestSupport {
    protected Address replicaAddress;
    protected AtomixReplica replica;
    protected AtomixClient client;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        createComponents().entrySet().stream()
            .forEach(e -> registry.bind(e.getKey(), e.getValue()));

        return registry;
    }

    @Override
    protected void doPreSetup() throws Exception {
        replicaAddress = AtomixFactory.address("127.0.0.1");
        replica = AtomixFactory.replica(replicaAddress);
        client = AtomixFactory.client(replicaAddress);

        super.doPreSetup();
    }

    @Override
    public void tearDown() throws Exception {
        if (client != null) {
            client.close().join();
            client = null;
        }

        if (replica != null) {
            replica.shutdown().join();
            replica.leave().join();
            replica = null;
        }

        super.tearDown();
    }

    protected abstract Map<String, Component> createComponents();

    // *************************************
    // properties
    // *************************************

    protected Address getReplicaAddress() {
        return replicaAddress;
    }

    protected AtomixReplica getReplica() {
        return replica;
    }

    protected AtomixClient getClient() {
        return client;
    }
}
