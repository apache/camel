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
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.zookeeper.SequenceComparator;
import org.apache.camel.component.zookeeper.ZooKeeperEndpoint;
import org.apache.camel.component.zookeeper.ZooKeeperMessage;
import org.apache.camel.impl.JavaUuidGenerator;
import org.apache.camel.spi.UuidGenerator;

import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>ZooKeeperElection</code> uses the leader election capabilities of a
 * ZooKeeper cluster to control which nodes are enabled. It is typically used in
 * fail-over scenarios controlling identical instances of an application across
 * a cluster of Camel based servers. <p> The election is configured with a 'top
 * n' number of servers that should be marked as master, for a simple
 * master/slave scenario this would be 1. Each instance will execute the
 * election algorithm to obtain its position in the hierarchy of servers, if it
 * is within the 'top n' servers then the node is enabled and isMaster() will
 * return 'true'. If not it waits for a change in the leader hierarchy and then
 * reruns this scenario to see if it is now in the top n. <p> All instances of
 * the election must also be configured with the same path on the ZooKeeper
 * cluster where the election will be carried out. It is good practice for this
 * to indicate the application e.g. <tt>/someapplication/someroute/</tt> note
 * that these nodes should exist before using the election. <p> See <a
 * href="http://hadoop.apache.org/zookeeper/docs/current/recipes.html#sc_leaderElection">
 * for more on how Leader election</a> is archived with ZooKeeper.
 */
public class ZooKeeperElection {

    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperElection.class);
    private final ProducerTemplate producerTemplate;
    private final CamelContext camelContext;
    private final String uri;
    private final String candidateName;
    private final Lock lock = new ReentrantLock();
    private final CountDownLatch electionComplete = new CountDownLatch(1);
    private AtomicBoolean masterNode = new AtomicBoolean();
    private volatile boolean isCandidateCreated;
    private int enabledCount = 1;
    private UuidGenerator uuidGenerator = new JavaUuidGenerator();
    private final List<ElectionWatcher> watchers = new ArrayList<ElectionWatcher>();

    public ZooKeeperElection(CamelContext camelContext, String uri, int enabledCount) {
        this(camelContext.createProducerTemplate(), camelContext, uri, enabledCount);
    }

    public ZooKeeperElection(ProducerTemplate producerTemplate, CamelContext camelContext, String uri, int enabledCount) {
        this.camelContext = camelContext;
        this.producerTemplate = producerTemplate;
        this.uri = uri;
        this.enabledCount = enabledCount;
        this.candidateName = createCandidateName();
    }

    public boolean isMaster() {
        if (!isCandidateCreated) {
            testAndCreateCandidateNode();
            awaitElectionResults();

        }
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

    private void testAndCreateCandidateNode() {
        try {
            lock.lock();
            if (!isCandidateCreated) {
                createCandidateNode(camelContext);
                isCandidateCreated = true;
            }
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    private void awaitElectionResults() {
        while (electionComplete.getCount() > 0) {
            try {
                LOG.debug("Awaiting election results...");
                electionComplete.await();
            } catch (InterruptedException e1) {
                // do nothing here
            }
        }
    }

    private ZooKeeperEndpoint createCandidateNode(CamelContext camelContext) {
        LOG.info("Initializing ZookeeperElection with uri '{}'", uri);
        ZooKeeperEndpoint zep = camelContext.getEndpoint(uri, ZooKeeperEndpoint.class);
        zep.getConfiguration().setCreate(true);
        String fullpath = createFullPathToCandidate(zep);
        Exchange e = zep.createExchange();
        e.setPattern(ExchangePattern.InOut);
        e.getIn().setHeader(ZooKeeperMessage.ZOOKEEPER_NODE, fullpath);
        e.getIn().setHeader(ZooKeeperMessage.ZOOKEEPER_CREATE_MODE, CreateMode.EPHEMERAL_SEQUENTIAL);
        producerTemplate.send(zep, e);

        if (e.isFailed()) {
            LOG.warn("Error setting up election node " + fullpath, e.getException());
        } else {
            LOG.info("Candidate node '{}' has been created", fullpath);
            try {
                camelContext.addRoutes(new ElectoralMonitorRoute(zep));
            } catch (Exception ex) {
                LOG.warn("Error configuring ZookeeperElection", ex);
            }
        }
        return zep;

    }

    private String createFullPathToCandidate(ZooKeeperEndpoint zep) {
        String fullpath = zep.getConfiguration().getPath();
        if (!fullpath.endsWith("/")) {
            fullpath += "/";
        }
        fullpath += candidateName;
        return fullpath;
    }

    private void handleException(Exception e) {
        throw new RuntimeException(e.getMessage(), e);
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

    public boolean removeElectionWatcher(ElectionWatcher o) {
        return watchers.remove(o);
    }

    private class ElectoralMonitorRoute extends RouteBuilder {

        private SequenceComparator comparator = new SequenceComparator();
        private ZooKeeperEndpoint zep;

        ElectoralMonitorRoute(ZooKeeperEndpoint zep) {
            this.zep = zep;
            zep.getConfiguration().setListChildren(true);
            zep.getConfiguration().setSendEmptyMessageOnDelete(true);
            zep.getConfiguration().setRepeat(true);
        }

        @Override
        public void configure() throws Exception {

            /**
             * TODO: this is cheap cheerful but suboptimal; it suffers from the
             * 'herd effect' that on any change to the candidates list every
             * policy instance will ask for the entire candidate list again.
             * This is fine for small numbers of nodes (for scenarios like
             * Master-Slave it is perfect) but could get noisy if large numbers
             * of nodes were involved. <p> Better would be to find the position
             * of this node in the list and watch the node in the position ahead
             * node ahead of this and only request the candidate list when its
             * status changes. This will require enhancing the consumer to allow
             * custom operation lists.
             */
            from(zep).id("election-route-" + candidateName).sort(body(), comparator).process(new Processor() {
                @Override
                public void process(Exchange e) throws Exception {
                    @SuppressWarnings("unchecked")
                    List<String> candidates = e.getIn().getMandatoryBody(List.class);
                    // we cannot use the binary search here and the candidates a not sorted in the normal way
                    /**
                     * check if the item at this location starts with this nodes
                     * candidate name
                     */
                    int location = findCandidateLocationInCandidatesList(candidates, candidateName); 
                    if (location != -1) {
                        // set the nodes
                        masterNode.set(location <= enabledCount);
                        LOG.debug("This node is number '{}' on the candidate list, election is configured for the top '{}'. this node will be {}",
                                new Object[]{location, enabledCount, masterNode.get() ? "enabled" : "disabled"}
                        );
                    }
                    electionComplete.countDown();

                    notifyElectionWatchers();
                }

                private int findCandidateLocationInCandidatesList(List<String> candidates, String candidateName) {
                
                    for (int location = 1; location <= candidates.size(); location++) {
                        if (candidates.get(location - 1).startsWith(candidateName)) {
                            return location;
                        }
                    }
                    return -1;
                }
            });
        }
    }
}
