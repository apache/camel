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
package org.apache.camel.component.zookeeper.policy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.StatefulService;
import org.apache.camel.impl.JavaUuidGenerator;
import org.apache.camel.spi.UuidGenerator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreV2;
import org.apache.curator.framework.recipes.locks.Lease;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>CuratorMultiMasterLeaderElection</code> uses the leader election capabilities of a
 * ZooKeeper cluster to control which nodes are enabled. It is typically used in
 * fail-over scenarios controlling identical instances of an application across
 * a cluster of Camel based servers. <p> The election is configured providing the number of instances that are required
 * to be active..
 * <p> All instances of the election must also be configured with the same path on the ZooKeeper
 * cluster where the election will be carried out. It is good practice for this
 * to indicate the application e.g. <tt>/someapplication/someroute/</tt> note
 * that these nodes should exist before using the election. <p> See <a
 * href="http://hadoop.apache.org/zookeeper/docs/current/recipes.html#sc_leaderElection">
 * for more on how Leader election</a> is archived with ZooKeeper.
 */
public class CuratorMultiMasterLeaderElection implements ConnectionStateListener {

    private static final Logger LOG = LoggerFactory.getLogger(CuratorMultiMasterLeaderElection.class);

    private final String candidateName;
    private final List<ElectionWatcher> watchers = new ArrayList<ElectionWatcher>();
    private final int desiredActiveNodes;
    private AtomicBoolean activeNode = new AtomicBoolean(false);
    private UuidGenerator uuidGenerator = new JavaUuidGenerator();
    private InterProcessSemaphoreV2 leaderSelector;
    private CuratorFramework client;
    private Lease lease;

    public CuratorMultiMasterLeaderElection(String uri, int desiredActiveNodes) {
        this.candidateName = createCandidateName();
        this.desiredActiveNodes = desiredActiveNodes;

        String connectionString = uri.substring(1 + uri.indexOf(':')).split("/")[0];
        String protocol = uri.substring(0, uri.indexOf(':'));
        String path = uri.replace(protocol + ":" + connectionString, "");
        client = CuratorFrameworkFactory.newClient(connectionString, new ExponentialBackoffRetry(1000, 3));
        client.getConnectionStateListenable().addListener(this);
        leaderSelector = new InterProcessSemaphoreV2(client, path, this.desiredActiveNodes);
        client.start();


    }

    // stolen from org/apache/camel/processor/CamelInternalProcessor
    public static boolean isCamelStopping(CamelContext context) {
        if (context instanceof StatefulService) {
            StatefulService ss = (StatefulService) context;
            return ss.isStopping() || ss.isStopped();
        }
        return false;
    }

    public void shutdownClients() {
        try {
            leaderSelector.returnLease(lease);
        } finally {
            client.close();
        }
    }

    /*
     * Blocking method
     */
    public void requestResource() {
        LOG.info("Requested to become active from {}", candidateName);
        try {
            lease = leaderSelector.acquire();
        } catch (Exception e) {
            throw new RuntimeException("Unable to obtain access to become a leader node.");
        }
        LOG.info("{} is now active", candidateName);
        activeNode.set(true);
        notifyElectionWatchers();
    }

    public boolean isMaster() {
        return activeNode.get();
    }

    private String createCandidateName() {
        StringBuilder builder = new StringBuilder();
        try {
            /* UUID would be enough, also using hostname for human readability */
            builder.append(InetAddress.getLocalHost().getCanonicalHostName());
        } catch (UnknownHostException ex) {
            LOG.warn("Failed to get the local hostname.", ex);
            builder.append("unknown-host");
        }
        builder.append("-").append(uuidGenerator.generateUuid());
        return builder.toString();
    }

    public String getCandidateName() {
        return candidateName;
    }

    private void notifyElectionWatchers() {
        for (ElectionWatcher watcher : watchers) {
            try {
                watcher.electionResultChanged();
            } catch (Exception e) {
                LOG.warn("Election watcher " + watcher + " of type " + watcher.getClass() + " threw an exception.", e);
            }
        }
    }

    public boolean addElectionWatcher(ElectionWatcher e) {
        return watchers.add(e);
    }

    @Override
    public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
        switch (connectionState) {
        case SUSPENDED:
        case LOST:
            LOG.info("Received {} state from connection. Giving up lock.", connectionState);

            try {
                leaderSelector.returnLease(lease);
            } finally {
                this.activeNode.set(false);
                notifyElectionWatchers();
            }

            break;
        default:
            LOG.info("Connection state changed: {}", connectionState);
            requestResource();

        }
    }

}


