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
package org.apache.camel.component.kubernetes.cluster.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterView;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records leadership changes and allow to do assertions.
 */
public class LeaderRecorder implements CamelClusterEventListener.Leadership {

    private static final Logger LOG = LoggerFactory.getLogger(LeaderRecorder.class);

    private List<LeadershipInfo> leaderships = new CopyOnWriteArrayList<>();

    @Override
    public void leadershipChanged(CamelClusterView view, Optional<CamelClusterMember> leader) {
        LOG.info("Cluster view {} - leader changed to: {}", view.getLocalMember(), leader);
        this.leaderships.add(new LeadershipInfo(leader.map(CamelClusterMember::getId).orElse(null), System.currentTimeMillis()));
    }

    public List<LeadershipInfo> getLeadershipInfo() {
        return leaderships;
    }

    public void waitForAnyLeader(long time, TimeUnit unit) {
        waitForLeader(leader -> leader != null, time, unit);
    }

    public void waitForALeaderChange(long time, TimeUnit unit) {
        String current = getCurrentLeader();
        waitForLeader(leader -> !Objects.equals(current, leader), time, unit);
    }

    public void waitForANewLeader(String current, long time, TimeUnit unit) {
        waitForLeader(leader -> leader != null && !Objects.equals(current, leader), time, unit);
    }

    public void waitForLeader(Predicate<String> as, long time, TimeUnit unit) {
        long start = System.currentTimeMillis();
        while (!as.test(getCurrentLeader())) {
            if (System.currentTimeMillis() - start > TimeUnit.MILLISECONDS.convert(time, unit)) {
                Assert.fail("Timeout while waiting for condition");
            }
            doWait(50);
        }
    }

    private void doWait(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public String getCurrentLeader() {
        if (leaderships.size() > 0) {
            return leaderships.get(leaderships.size() - 1).getLeader();
        }
        return null;
    }

    public Long getLastTimeOf(Predicate<String> p) {
        List<LeadershipInfo> lst = new ArrayList<>(leaderships);
        Collections.reverse(lst);
        for (LeadershipInfo info : lst) {
            if (p.test(info.getLeader())) {
                return info.getChangeTimestamp();
            }
        }
        return null;
    }

    public static class LeadershipInfo {
        private String leader;
        private long changeTimestamp;

        public LeadershipInfo(String leader, long changeTimestamp) {
            this.leader = leader;
            this.changeTimestamp = changeTimestamp;
        }

        public String getLeader() {
            return leader;
        }

        public long getChangeTimestamp() {
            return changeTimestamp;
        }
    }

}
