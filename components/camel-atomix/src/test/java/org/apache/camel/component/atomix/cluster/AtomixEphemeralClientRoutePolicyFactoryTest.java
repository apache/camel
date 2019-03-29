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
package org.apache.camel.component.atomix.cluster;

import java.util.Collections;

import io.atomix.catalyst.transport.Address;
import org.apache.camel.cluster.CamelClusterService;

public final class AtomixEphemeralClientRoutePolicyFactoryTest extends AtomixClientRoutePolicyFactoryTestSupport {
    @Override
    protected CamelClusterService createClusterService(String id, Address bootstrapNode) {
        AtomixClusterClientService service = new AtomixClusterClientService();
        service.setId("node-" + id);
        service.setNodes(Collections.singletonList(bootstrapNode));
        service.setEphemeral(true);

        return service;
    }
}
