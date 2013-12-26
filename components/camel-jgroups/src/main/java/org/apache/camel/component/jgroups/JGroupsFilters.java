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
package org.apache.camel.component.jgroups;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.jgroups.Address;
import org.jgroups.View;

import static org.apache.camel.component.jgroups.JGroupsEndpoint.HEADER_JGROUPS_CHANNEL_ADDRESS;

public final class JGroupsFilters {

    private static final int COORDINATOR_NODE_INDEX = 0;

    private JGroupsFilters() {
    }

    /**
     * Creates predicate rejecting messages that are instances of {@link org.jgroups.View}, but have not been received
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
                if (body instanceof View) {
                    View view = (View) body;
                    Address channelAddress = exchange.getIn().getHeader(HEADER_JGROUPS_CHANNEL_ADDRESS, Address.class);
                    return channelAddress.equals(view.getMembers().get(COORDINATOR_NODE_INDEX));
                }
                return true;
            }
        };

    }

}
