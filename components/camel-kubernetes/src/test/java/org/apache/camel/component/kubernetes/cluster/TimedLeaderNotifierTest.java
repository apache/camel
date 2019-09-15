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
package org.apache.camel.component.kubernetes.cluster;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.component.kubernetes.cluster.lock.KubernetesClusterEvent;
import org.apache.camel.component.kubernetes.cluster.lock.TimedLeaderNotifier;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test the behavior of the timed notifier.
 */
public class TimedLeaderNotifierTest {

    private CamelContext context;

    private TimedLeaderNotifier notifier;

    private volatile Optional<String> currentLeader;

    private volatile Set<String> currentMembers;

    @Before
    public void init() throws Exception {
        this.context = new DefaultCamelContext();
        this.context.start();

        this.notifier = new TimedLeaderNotifier(context, e -> {
            if (e instanceof KubernetesClusterEvent.KubernetesClusterLeaderChangedEvent) {
                currentLeader = ((KubernetesClusterEvent.KubernetesClusterLeaderChangedEvent)e).getData();
            } else if (e instanceof KubernetesClusterEvent.KubernetesClusterMemberListChangedEvent) {
                currentMembers = ((KubernetesClusterEvent.KubernetesClusterMemberListChangedEvent)e).getData();
            }
        });
        this.notifier.start();
    }

    @After
    public void destroy() throws Exception {
        this.notifier.stop();
        this.context.stop();
    }

    @Test
    public void testMultipleCalls() throws Exception {
        Set<String> members = new TreeSet<>(Arrays.asList("one", "two", "three"));
        notifier.refreshLeadership(Optional.of("one"), System.currentTimeMillis(), 50L, members);
        notifier.refreshLeadership(Optional.of("two"), System.currentTimeMillis(), 50L, members);
        notifier.refreshLeadership(Optional.of("three"), System.currentTimeMillis(), 5000L, members);
        Thread.sleep(80);
        assertEquals(Optional.of("three"), currentLeader);
        assertEquals(members, currentMembers);
    }

    @Test
    public void testExpiration() throws Exception {
        Set<String> members = new TreeSet<>(Arrays.asList("one", "two", "three"));
        notifier.refreshLeadership(Optional.of("one"), System.currentTimeMillis(), 50L, members);
        notifier.refreshLeadership(Optional.of("two"), System.currentTimeMillis(), 50L, members);
        Thread.sleep(160);
        assertEquals(Optional.empty(), currentLeader);
        assertEquals(members, currentMembers);
        notifier.refreshLeadership(Optional.of("three"), System.currentTimeMillis(), 5000L, members);
        Thread.sleep(80);
        assertEquals(Optional.of("three"), currentLeader);
        assertEquals(members, currentMembers);
    }

    @Test
    public void testMemberChanging() throws Exception {
        Set<String> members1 = Collections.singleton("one");
        Set<String> members2 = new TreeSet<>(Arrays.asList("one", "two"));
        notifier.refreshLeadership(Optional.of("one"), System.currentTimeMillis(), 50L, members1);
        notifier.refreshLeadership(Optional.of("two"), System.currentTimeMillis(), 5000L, members2);
        Thread.sleep(80);
        assertEquals(Optional.of("two"), currentLeader);
        assertEquals(members2, currentMembers);
    }

    @Test
    public void testOldData() throws Exception {
        Set<String> members = new TreeSet<>(Arrays.asList("one", "two", "three"));
        notifier.refreshLeadership(Optional.of("one"), System.currentTimeMillis(), 1000L, members);
        Thread.sleep(80);
        notifier.refreshLeadership(Optional.of("two"), System.currentTimeMillis() - 1000, 900L, members);
        Thread.sleep(80);
        assertEquals(Optional.empty(), currentLeader);
    }

    @Test
    public void testNewLeaderEmpty() throws Exception {
        Set<String> members = new TreeSet<>(Arrays.asList("one", "two", "three"));
        notifier.refreshLeadership(Optional.of("one"), System.currentTimeMillis(), 1000L, members);
        Thread.sleep(80);
        notifier.refreshLeadership(Optional.empty(), null, null, members);
        Thread.sleep(80);
        assertEquals(Optional.empty(), currentLeader);
    }

}
