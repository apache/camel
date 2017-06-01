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
package org.apache.camel.component.atomix.ha;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.atomix.AtomixReplica;
import io.atomix.catalyst.transport.Address;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.ha.AbstractCamelCluster;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtomixCluster extends AbstractCamelCluster<AtomixClusterView> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixCluster.class);

    private final AtomixReplica atomix;
    private final List<Address> addresses;

    public AtomixCluster(AtomixReplica atomix) {
        this(null, atomix, Collections.emptyList());
    }

    public AtomixCluster(AtomixReplica atomix, List<Address> addresses) {
       this(null, atomix, addresses);
    }

    public AtomixCluster(CamelContext camelContext, AtomixReplica atomix, List<Address> addresses) {
        super("camel-atomix", camelContext);

        this.atomix = atomix;
        this.addresses = new ArrayList<>(addresses);
    }

    @Override
    protected void doStart() throws Exception {
        // Assume that if addresses are provided the cluster needs be bootstrapped.
        if (ObjectHelper.isNotEmpty(addresses)) {
            LOGGER.debug("Bootstrap cluster for nodes: {}", addresses);
            this.atomix.bootstrap(addresses).join();
            LOGGER.debug("Bootstrap cluster done");
        }

        super.doStart();
    }

    @Override
    protected AtomixClusterView doCreateView(String namespace) throws Exception {
        return new AtomixClusterView(this, namespace, atomix);
    }
}
