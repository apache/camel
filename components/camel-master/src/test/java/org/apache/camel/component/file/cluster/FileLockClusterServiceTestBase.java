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
package org.apache.camel.component.file.cluster;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

abstract class FileLockClusterServiceTestBase {
    protected static final String NAMESPACE = "test-ns";

    @TempDir
    protected Path clusterDir;
    protected Path lockFile;
    protected Path dataFile;

    @BeforeEach
    public void beforeEach() {
        lockFile = clusterDir.resolve(NAMESPACE);
        dataFile = clusterDir.resolve(NAMESPACE + ".dat");
    }

    protected CamelContext createCamelContext() throws Exception {
        return createCamelContext(new ClusterConfig());
    }

    protected CamelContext createCamelContext(ClusterConfig config) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addService(createFileLockClusterService(config));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("master:%s:timer:clustered?delay=1&period=100&repeatCount=%d", NAMESPACE, config.getTimerRepeatCount())
                        .routeId("clustered")
                        .log(LoggingLevel.DEBUG, "Timer fired for ${camelId}")
                        .to("mock:result");
            }
        });
        return context;
    }

    protected FileLockClusterService createFileLockClusterService(ClusterConfig config) {
        FileLockClusterService service = new FileLockClusterService();
        service.setAcquireLockDelay(config.getAcquireLockDelay());
        service.setAcquireLockInterval(1);
        service.setRoot(clusterDir.toString());
        service.setHeartbeatTimeoutMultiplier(config.getHeartbeatTimeoutMultiplier());
        service.setAcquireLeadershipBackoff(config.getAcquireLeadershipBackoff());
        return service;
    }

    protected CamelClusterMember getClusterMember(CamelContext camelContext) throws Exception {
        return getClusterView(camelContext).getLocalMember();
    }

    protected CamelClusterView getClusterView(CamelContext camelContext) throws Exception {
        FileLockClusterService fileLockClusterService = camelContext.hasService(FileLockClusterService.class);
        return fileLockClusterService.getView(NAMESPACE);
    }

    static final class ClusterConfig {
        private long acquireLockDelay = 1;
        private long timerRepeatCount = 5;
        private int heartbeatTimeoutMultiplier = 5;
        private long acquireLeadershipBackoff = 0;

        long getAcquireLockDelay() {
            return acquireLockDelay;
        }

        void setAcquireLockDelay(long acquireLockDelay) {
            this.acquireLockDelay = acquireLockDelay;
        }

        long getTimerRepeatCount() {
            return timerRepeatCount;
        }

        void setTimerRepeatCount(long timerRepeatCount) {
            this.timerRepeatCount = timerRepeatCount;
        }

        long getStartupDelayWithOffsetMillis() {
            return TimeUnit.SECONDS.toMillis(getAcquireLockDelay()) + 500;
        }

        public int getHeartbeatTimeoutMultiplier() {
            return heartbeatTimeoutMultiplier;
        }

        public void setHeartbeatTimeoutMultiplier(int heartbeatTimeoutMultiplier) {
            this.heartbeatTimeoutMultiplier = heartbeatTimeoutMultiplier;
        }

        public long getAcquireLeadershipBackoff() {
            return acquireLeadershipBackoff;
        }

        public void setAcquireLeadershipBackoff(long acquireLeadershipBackoff) {
            this.acquireLeadershipBackoff = acquireLeadershipBackoff;
        }
    }
}
