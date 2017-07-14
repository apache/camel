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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.StatefulService;
import org.apache.camel.impl.JavaUuidGenerator;
import org.apache.camel.spi.UuidGenerator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>CuratorLeaderElection</code> uses the leader election capabilities of a
 * ZooKeeper cluster to control which nodes are enabled. It is typically used in
 * fail-over scenarios controlling identical instances of an application across
 * a cluster of Camel based servers. <p> The election is configured with a single
 * server that should be marked as master.
 * <p> All instances of the election must also be configured with the same path on the ZooKeeper
 * cluster where the election will be carried out. It is good practice for this
 * to indicate the application e.g. <tt>/someapplication/someroute/</tt> note
 * that these nodes should exist before using the election. <p> See <a
 * href="http://hadoop.apache.org/zookeeper/docs/current/recipes.html#sc_leaderElection">
 * for more on how Leader election</a> is archived with ZooKeeper.
 */
public class CuratorLeaderElection {

    private static final Logger LOG = LoggerFactory.getLogger(CuratorLeaderElection.class);
    private final CamelContext camelContext;
    private final String uri;

    private final String candidateName;
    private final Lock lock = new ReentrantLock();
    private final CountDownLatch electionComplete = new CountDownLatch(1);
    private final List<ElectionWatcher> watchers = new ArrayList<ElectionWatcher>();
    private AtomicBoolean masterNode = new AtomicBoolean(false);
    private volatile boolean isCandidateCreated;
    private int enabledCount = 1;
    private UuidGenerator uuidGenerator = new JavaUuidGenerator();
    private LeaderSelector leaderSelector;
    private CuratorFramework client;

    public CuratorLeaderElection(CamelContext camelContext, String uri) {
        this.camelContext = camelContext;
        this.uri = uri;
        this.candidateName = createCandidateName();

        String connectionString = uri.substring(1 + uri.indexOf(':')).split("/")[0];
        String protocol = uri.substring(0, uri.indexOf(':'));
        String path = uri.replace(protocol + ":" + connectionString, "");
        client = CuratorFrameworkFactory.newClient(connectionString, new ExponentialBackoffRetry(1000, 3));
        client.start();

        leaderSelector = new LeaderSelector(client, path, new CamelLeaderElectionListener());
        leaderSelector.start();
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
            leaderSelector.close();
        } finally {
            client.close();
        }
    }

    public boolean isMaster() {
        return masterNode.get();
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

    class CamelLeaderElectionListener extends LeaderSelectorListenerAdapter {

        @Override
        public void takeLeadership(CuratorFramework curatorFramework) throws Exception {
            masterNode.set(true);
            LOG.info("{} is now leader", getCandidateName());
            notifyElectionWatchers();

            // this is supposed to never return as long as it wants to keep its own leader status
            while (!isCamelStopping(camelContext)) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.interrupted();
                    break;
                }
            }
            masterNode.set(false);
            LOG.info("{} has given up its own leadership", getCandidateName());
            notifyElectionWatchers();
        }
    }
}


