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

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.camel.component.zookeeper.ZooKeeperTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZookeeperElectionTest extends ZooKeeperTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperElectionTest.class);

    private static final String NODE_BASE_KEY = "/someapp";
    private static final String NODE_PARTICULAR_KEY = "/someapp/somepolicy";
    private static final String ELECTION_URI = "zookeeper:localhost:39913/someapp/somepolicy";

    @Before
    public void before() throws Exception {
        // set up the parent used to control the election
        client.createPersistent(NODE_BASE_KEY, "App node to contain policy election nodes...");
        client.createPersistent(NODE_PARTICULAR_KEY, "Policy node used by route policy to control routes...");
    }

    @After
    public void after() throws Exception {
        client.deleteAll(NODE_PARTICULAR_KEY);
        client.delete(NODE_BASE_KEY);
    }

    @Test
    public void masterCanBeElected() throws Exception {
        ZooKeeperElection candidate = new ZooKeeperElection(template, context, ELECTION_URI, 1);
        assertTrue("The only election candidate was not elected as master.", candidate.isMaster());
    }

    @Test
    public void masterAndSlave() throws Exception {
        final DefaultCamelContext candidateOneContext = createNewContext();
        final DefaultCamelContext candidateTwoContext = createNewContext();

        ZooKeeperElection electionCandidate1 = createElectionCandidate(candidateOneContext, 1);
        assertTrue("The first candidate was not elected.", electionCandidate1.isMaster());
        ZooKeeperElection electionCandidate2 = createElectionCandidate(candidateTwoContext, 1);
        assertFalse("The second candidate should not have been elected.", electionCandidate2.isMaster());
    }

    @Test
    public void testMasterGoesAway() throws Exception {
        final DefaultCamelContext candidateOneContext = createNewContext();
        final DefaultCamelContext candidateTwoContext = createNewContext();

        ZooKeeperElection electionCandidate1 = createElectionCandidate(candidateOneContext, 1);
        assertTrue("The first candidate was not elected.", electionCandidate1.isMaster());
        ZooKeeperElection electionCandidate2 = createElectionCandidate(candidateTwoContext, 1);
        assertFalse("The second candidate should not have been elected.", electionCandidate2.isMaster());

        LOG.debug("About to shutdown the first candidate.");

        candidateOneContext.stop(); // the first candidate was killed.
        assertIsMaster(electionCandidate2);
    }

    @Test
    public void testDualMaster() throws Exception {
        final DefaultCamelContext candidateOneContext = createNewContext();
        final DefaultCamelContext candidateTwoContext = createNewContext();

        ZooKeeperElection electionCandidate1 = createElectionCandidate(candidateOneContext, 2);
        assertTrue("The first candidate was not elected.", electionCandidate1.isMaster());
        
        ZooKeeperElection electionCandidate2 = createElectionCandidate(candidateTwoContext, 2);
        assertIsMaster(electionCandidate2);
    }

    @Test
    public void testWatchersAreNotified() throws Exception {
        final DefaultCamelContext candidateOneContext = createNewContext();
        final DefaultCamelContext candidateTwoContext = createNewContext();

        final AtomicBoolean notified = new AtomicBoolean(false);
        ElectionWatcher watcher = new ElectionWatcher() {
            @Override public void electionResultChanged() { notified.set(true); }
        };

        ZooKeeperElection electionCandidate1 = createElectionCandidate(candidateOneContext, 2);
        assertTrue("The first candidate was not elected.", electionCandidate1.isMaster());
        electionCandidate1.addElectionWatcher(watcher);
        ZooKeeperElection electionCandidate2 = createElectionCandidate(candidateTwoContext, 2);
        electionCandidate2.isMaster();
        assertTrue("The first candidate should have had it's watcher notified", notified.get());
    }

    private DefaultCamelContext createNewContext() throws Exception {
        DefaultCamelContext controlledContext = new DefaultCamelContext();
        controlledContext.start();
        return controlledContext;
    }

    private ZooKeeperElection createElectionCandidate(final DefaultCamelContext context, int masterCount) {
        return new ZooKeeperElection(context.createProducerTemplate(), context, ELECTION_URI, masterCount);
    }

    private void assertIsMaster(ZooKeeperElection electionCandidate) throws InterruptedException {
        // Need to wait for a while to be elected.
        long timeout = System.currentTimeMillis() + 5000;
        
        while (!electionCandidate.isMaster() && timeout > System.currentTimeMillis()) {
            Thread.sleep(200);
        }
        
        assertTrue("The candidate should have been elected.", electionCandidate.isMaster());
    }
}
