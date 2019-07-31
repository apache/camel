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
package org.apache.camel.component.jgroups;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.jgroups.Address;
import org.jgroups.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jgroups.JGroupsEndpoint.HEADER_JGROUPS_CHANNEL_ADDRESS;

/**
 * JGroups-specific filters factory.
 */
public final class JGroupsFilters {

    private static final Logger LOG = LoggerFactory.getLogger(JGroupsFilters.class);

    /**
     * The index of the coordinator node in the {@code org.jgroups.View} instance. Coordinator node is always the first
     * one in the members' list of the cluster view.
     */
    private static final int COORDINATOR_NODE_INDEX = 0;

    private JGroupsFilters() {
    }

    /**
     * Creates predicate rejecting messages that are instances of {@code org.jgroups.View}, but have not been received
     * by the coordinator JGroups node. This filter is useful for keeping only view messages indicating that receiving
     * endpoint is a master node.
     *
     * @return predicate filtering out non-coordinator view messages.
     */
    public static Predicate dropNonCoordinatorViews() {
        return new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                LOG.debug("Filtering message {}.", body);
                if (body instanceof View) {
                    View view = (View) body;
                    Address coordinatorNodeAddress =  view.getMembers().get(COORDINATOR_NODE_INDEX);
                    Address channelAddress = exchange.getIn().getHeader(HEADER_JGROUPS_CHANNEL_ADDRESS, Address.class);
                    LOG.debug("Comparing endpoint channel address {} against the coordinator node address {}.",
                            channelAddress, coordinatorNodeAddress);
                    return channelAddress.equals(coordinatorNodeAddress);
                }
                LOG.debug("Body {} is not an instance of org.jgroups.View . Skipping filter.", body);
                return false;
            }
        };
    }

}
