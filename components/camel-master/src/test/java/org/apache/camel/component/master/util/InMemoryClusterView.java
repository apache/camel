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
package org.apache.camel.component.master.util;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

import org.apache.camel.ha.CamelClusterMember;
import org.apache.camel.ha.CamelClusterService;
import org.apache.camel.impl.ha.AbstractCamelClusterView;
import org.apache.camel.util.ObjectHelper;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.PhysicalAddress;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.protocols.TCP;
import org.jgroups.protocols.TCPPING;
import org.jgroups.stack.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemoryClusterView extends AbstractCamelClusterView {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryClusterView.class);

    private JChannel channel;

    protected InMemoryClusterView(CamelClusterService cluster, String namespace) {
        super(cluster, namespace);
    }

    @Override
    public Optional<CamelClusterMember> getMaster() {
        return channel != null
            ? Optional.of(new InMemoryClusterMember(channel))
            : Optional.empty();
    }

    @Override
    public CamelClusterMember getLocalMember() {
        return new LocalClusterMember();
    }

    @Override
    public List<CamelClusterMember> getMembers() {
        if (channel != null) {
            channel.getView().getMembers().stream()
                .map(ClusterMember::new)
                .collect(toList());
        }

        return Collections.emptyList();
    }

    @Override
    protected void doStart() throws Exception {
        final int index = getClusterService().unwrap(InMemoryClusterService.class).getIndex();
        final List<Integer> ports = getClusterService().unwrap(InMemoryClusterService.class).getPorts();
        final List<PhysicalAddress> addresses = new ArrayList<>();

        for (Integer port: ports) {
            addresses.add(new IpAddress("127.0.0.1", port));
        }

        this.channel = new JChannel(getClass().getResourceAsStream("/jgroups-tcp.xml"));

        TCP tcp = this.channel.getProtocolStack().findProtocol(TCP.class);
        tcp.setBindAddress(InetAddress.getByName("127.0.0.1"));
        tcp.setBindPort(ports.get(index));

        TCPPING tcpping  = this.channel.getProtocolStack().findProtocol(TCPPING.class);
        tcpping.setInitialHosts(addresses);

        this.channel.setReceiver(new ReceiverAdapter() {
            @Override
            public void viewAccepted(View view) {
                fireLeadershipChangedEvent(new ClusterMember(view.getMembers().get(0)));
            }
        });

        this.channel.connect(getNamespace());
    }

    @Override
    protected void doStop() throws Exception {
        if (channel != null) {
            channel.close();
        }
    }

    // ***********************************
    //
    // ***********************************

    private class LocalClusterMember implements CamelClusterMember {
        @Override
        public boolean isMaster() {
            if (channel == null) {
                return false;
            }

            List<Address> members = channel.view().getMembers();

            if (ObjectHelper.isNotEmpty(members)) {
                LOGGER.info("master={}, channel={}, members={}", members.get(0), channel.getAddress(), members);
                return members.get(0).equals(channel.getAddress());
            }

            return false;
        }

        @Override
        public String getId() {
            return channel != null ? channel.getAddressAsString() : "local";
        }
    }

    private class ClusterMember implements CamelClusterMember {
        private final Address address;

        public ClusterMember(Address address) {
            this.address = address;
        }

        @Override
        public boolean isMaster() {
            final List<Address> members = channel.view().getMembers();

            return ObjectHelper.isNotEmpty(members)
                ? members.get(0).equals(address)
                : false;
        }

        @Override
        public String getId() {
            return channel.getAddressAsString();
        }
    }

}
