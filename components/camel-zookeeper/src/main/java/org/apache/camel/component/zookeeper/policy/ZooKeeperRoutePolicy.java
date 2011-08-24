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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.String.format;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.zookeeper.SequenceComparator;
import org.apache.camel.component.zookeeper.ZooKeeperEndpoint;
import org.apache.camel.component.zookeeper.ZooKeeperMessage;
import org.apache.camel.impl.JavaUuidGenerator;
import org.apache.camel.impl.RoutePolicySupport;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.ExchangeHelper;
import org.apache.zookeeper.CreateMode;

/**
 * <code>ZooKeeperRoutePolicy</code> uses the leader election capabilities of a
 * ZooKeeper cluster to control how routes are enabled. It is typically used in
 * fail-over scenarios controlling identical instances of a route across a
 * cluster of Camel based servers.
 * <p>
 * The policy is configured with a 'top n' number of routes that should be
 * allowed to start, for a master/slave scenario this would be 1. Each instance
 * of the policy will execute the election algorithm to obtain its position in
 * the hierarchy of servers, if it is within the 'top n' servers then the policy
 * is enabled and exchanges can be processed by the route. If not it waits for a
 * change in the leader hierarchy and then reruns this scenario to see if it is
 * now in the top n.
 * <p>
 * All instances of the policy must also be configured with the same path on the
 * ZooKeeper cluster where the election will be carried out. It is good practice
 * for this to indicate the application e.g. /someapplication/someroute/ note
 * that these nodes should exist before using the policy.
 * <p>
 * See @link{ http://hadoop.apache
 * .org/zookeeper/docs/current/recipes.html#sc_leaderElection} for more on how
 * Leader election is achieved with ZooKeeper.
 * 
 */
public class ZooKeeperRoutePolicy extends RoutePolicySupport {

    private String uri;

    private int enabledCount;

    private String candidateName;

    private final Lock lock = new ReentrantLock();

    private final CountDownLatch electionComplete = new CountDownLatch(1);

    private Set<Route> suspendedRoutes = new CopyOnWriteArraySet<Route>();

    private AtomicBoolean shouldProcessExchanges = new AtomicBoolean();

    private ProducerTemplate template;

    private boolean shouldStopConsumer = true;

    private UuidGenerator uuidGenerator = new JavaUuidGenerator();

    private boolean isCandidateCreated;

    public ZooKeeperRoutePolicy(String uri, int enabledCount) throws Exception {
        this.uri = uri;
        this.enabledCount = enabledCount;
        createCandidateName();
    }

    private void createCandidateName() throws Exception {
        /** UUID would be enough, also using hostname for human readability */
        StringBuilder b = new StringBuilder(InetAddress.getLocalHost().getCanonicalHostName());
        b.append("-").append(uuidGenerator.generateUuid());
        this.candidateName = b.toString();
    }

    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
        testAndCreateCandidateNode(route);

        awaitElectionResults();
        if (!shouldProcessExchanges.get()) {
            if (shouldStopConsumer) {
                stopConsumer(route);
            }

            IllegalStateException e = new IllegalStateException("Zookeeper based route policy prohibits processing exchanges, stopping route and failing the exchange");
            exchange.setException(e);

        } else {
            if (shouldStopConsumer) {
                startConsumer(route);
            }
        }
    }

    private void testAndCreateCandidateNode(Route route) {
        try {
            lock.lock();
            if (!isCandidateCreated) {
                createCandidateNode(route.getRouteContext().getCamelContext());
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
                electionComplete.await();
            } catch (InterruptedException e1) {
            }
        }
    }

    private void startConsumer(Route route) {
        try {
            lock.lock();
            if (suspendedRoutes.contains(route)) {
                startConsumer(route.getConsumer());
                suspendedRoutes.remove(route);
            }
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    private void stopConsumer(Route route) {
        try {
            lock.lock();
            // check that we should still suspend once the lock is acquired
            if (!suspendedRoutes.contains(route) && !shouldProcessExchanges.get()) {
                stopConsumer(route.getConsumer());
                suspendedRoutes.add(route);
            }
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    private void startAllStoppedConsumers() {
        try {
            lock.lock();
            if (!suspendedRoutes.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug(format("'%d' have been stopped previously by poilcy, restarting.", suspendedRoutes.size()));
                }
                for (Route suspended : suspendedRoutes) {
                    startConsumer(suspended.getConsumer());
                }
                suspendedRoutes.clear();
            }

        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    public boolean isShouldStopConsumer() {
        return shouldStopConsumer;
    }

    public void setShouldStopConsumer(boolean shouldStopConsumer) {
        this.shouldStopConsumer = shouldStopConsumer;
    }

    private ZooKeeperEndpoint createCandidateNode(CamelContext camelContext) {
        this.template = camelContext.createProducerTemplate();
        if (log.isInfoEnabled()) {
            log.info(format("Initializing ZookeeperRoutePolicy with uri '%s'", uri));
        }
        ZooKeeperEndpoint zep = (ZooKeeperEndpoint)camelContext.getEndpoint(uri);
        zep.getConfiguration().setCreate(true);
        String fullpath = createFullPathToCandidate(zep);
        Exchange e = zep.createExchange();
        e.setPattern(ExchangePattern.InOut);
        e.getIn().setHeader(ZooKeeperMessage.ZOOKEEPER_NODE, fullpath);
        e.getIn().setHeader(ZooKeeperMessage.ZOOKEEPER_CREATE_MODE, CreateMode.EPHEMERAL_SEQUENTIAL);
        template.send(zep, e);

        if (e.isFailed()) {
            log.error("Error setting up election node " + fullpath, e.getException());
        } else {
            if (log.isInfoEnabled()) {
                log.info(format("Candidate node '%s' has been created", fullpath));
            }
            try {
                if (zep != null) {
                    camelContext.addRoutes(new ElectoralMonitorRoute(zep));
                }
            } catch (Exception ex) {
                log.error("Error configuring ZookeeperRoutePolicy", ex);
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

    private class ElectoralMonitorRoute extends RouteBuilder {

        private SequenceComparator comparator = new SequenceComparator();
        
        private ZooKeeperEndpoint zep;

        public ElectoralMonitorRoute(ZooKeeperEndpoint zep) {
            this.zep = zep;
            zep.getConfiguration().setListChildren(true);
            zep.getConfiguration().setRepeat(true);
        }

        @Override
        public void configure() throws Exception {

            /**
             * TODO: this is cheap cheerful but suboptimal; it suffers from the
             * 'herd effect' that on any change to the candidates list every
             * policy instance will ask for the entire candidate list again.
             * This is fine for small numbers of nodes (for scenarios
             * like Master-Slave it is perfect) but could get noisy if
             * large numbers of nodes were involved.
             * <p>
             * Better would be to find the position of this node in the list and
             * watch the node in the position ahead node ahead of this and only
             * request the candidate list when its status changes. This will
             * require enhancing the consumer to allow custom operation lists.
             */
            from(zep).sort(body(), comparator).process(new Processor() {

                @SuppressWarnings("unchecked")
                public void process(Exchange e) throws Exception {
                    List<String> candidates = (List<String>)ExchangeHelper.getMandatoryInBody(e);

                    int location = Math.abs(Collections.binarySearch(candidates, candidateName));
                    /**
                     * check if the item at this location starts with this nodes
                     * candidate name
                     */
                    if (isOurCandidateAtLocationInCandidatesList(candidates, location)) {

                        shouldProcessExchanges.set(location <= enabledCount);
                        if (log.isDebugEnabled()) {
                            log.debug(format("This node is number '%d' on the candidate list, route is configured for the top '%d'. Exchange processing will be %s", location,
                                             enabledCount, shouldProcessExchanges.get() ? "enabled" : "disabled"));
                        }
                        startAllStoppedConsumers();
                    }
                    electionComplete.countDown();
                }

                private boolean isOurCandidateAtLocationInCandidatesList(List<String> candidates, int location) {
                    return location <= candidates.size() && candidates.get(location - 1).startsWith(candidateName);
                }
            });
        }
    }

}
