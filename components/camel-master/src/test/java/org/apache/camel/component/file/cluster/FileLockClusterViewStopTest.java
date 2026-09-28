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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLockClusterViewStopTest extends FileLockClusterServiceTestBase {

    @Test
    void stopViewOfLeaderNotifiesLeadershipLost() throws Exception {
        CamelContext context = createCamelContext();
        try {
            context.start();

            CamelClusterView view = getClusterView(context);
            await().atMost(20, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader());

            List<Optional<CamelClusterMember>> events = new CopyOnWriteArrayList<>();
            view.addEventListener(
                    (CamelClusterEventListener.Leadership) (v, leader) -> events.add(Optional.ofNullable(leader)));

            FileLockClusterService service = context.hasService(FileLockClusterService.class);
            service.stopView(NAMESPACE);

            assertFalse(view.getLocalMember().isLeader());
            // the listeners (such as the master consumer) must be told that the leadership is lost, as another member
            // can now take the lock and become the leader
            assertTrue(events.contains(Optional.empty()));
        } finally {
            context.stop();
        }
    }

    @Test
    void acquireLockIntervalBelowOneMillisecondIsRejected() throws Exception {
        CamelContext context = new DefaultCamelContext();
        FileLockClusterService service = new FileLockClusterService();
        service.setRoot(clusterDir.toString());
        service.setAcquireLockInterval(500, TimeUnit.MICROSECONDS);
        context.addService(service);
        try {
            context.start();

            Exception e = assertThrows(Exception.class, () -> service.getView(NAMESPACE));
            Throwable cause = e;
            while (cause.getCause() != null && !(cause instanceof IllegalArgumentException)) {
                cause = cause.getCause();
            }
            assertInstanceOf(IllegalArgumentException.class, cause);
        } finally {
            context.stop();
        }
    }
}
