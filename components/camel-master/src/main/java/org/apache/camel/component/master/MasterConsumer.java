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
package org.apache.camel.component.master;

import java.util.Optional;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StartupListener;
import org.apache.camel.SuspendableService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A consumer which is only really active when the {@link CamelClusterView} has
 * the leadership.
 */
@ManagedResource(description = "Managed Master Consumer")
public class MasterConsumer extends DefaultConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(MasterConsumer.class);

    private final CamelClusterService clusterService;
    private final MasterEndpoint masterEndpoint;
    private final Endpoint delegatedEndpoint;
    private final Processor processor;
    private final CamelClusterEventListener.Leadership leadershipListener;
    private Consumer delegatedConsumer;
    private volatile CamelClusterView view;

    public MasterConsumer(MasterEndpoint masterEndpoint, Processor processor, CamelClusterService clusterService) {
        super(masterEndpoint, processor);

        this.clusterService = clusterService;
        this.masterEndpoint = masterEndpoint;
        this.delegatedEndpoint = masterEndpoint.getEndpoint();
        this.processor = processor;
        this.leadershipListener = new LeadershipListener();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.debug("Using ClusterService instance {} (id={}, type={})", clusterService, clusterService.getId(), clusterService.getClass().getName());

        view = clusterService.getView(masterEndpoint.getNamespace());
        view.addEventListener(leadershipListener);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (view != null) {
            view.removeEventListener(leadershipListener);
            clusterService.releaseView(view);

            view = null;
        }

        ServiceHelper.stopAndShutdownServices(delegatedConsumer);
        ServiceHelper.stopAndShutdownServices(delegatedEndpoint);

        delegatedConsumer = null;
    }

    @Override
    protected void doResume() throws Exception {
        if (delegatedConsumer instanceof SuspendableService) {
            ((SuspendableService)delegatedConsumer).resume();
        }
        super.doResume();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (delegatedConsumer instanceof SuspendableService) {
            ((SuspendableService)delegatedConsumer).suspend();
        }
        super.doSuspend();
    }

    @ManagedAttribute(description = "Are we the master")
    public boolean isMaster() {
        return view != null
            ? view.getLocalMember().isLeader()
            : false;
    }

    // **************************************
    // Helpers
    // **************************************

    private synchronized void onLeadershipTaken() throws Exception {
        if (!isRunAllowed()) {
            return;
        }

        if (delegatedConsumer != null) {
            return;
        }

        delegatedConsumer = delegatedEndpoint.createConsumer(processor);
        if (delegatedConsumer instanceof StartupListener) {
            getEndpoint().getCamelContext().addStartupListener((StartupListener) delegatedConsumer);
        }

        ServiceHelper.startService(delegatedEndpoint);
        ServiceHelper.startService(delegatedConsumer);

        LOG.info("Leadership taken: consumer started: {}", delegatedEndpoint);
    }

    private synchronized void onLeadershipLost() throws Exception {
        ServiceHelper.stopAndShutdownServices(delegatedConsumer);
        ServiceHelper.stopAndShutdownServices(delegatedEndpoint);

        delegatedConsumer = null;

        LOG.info("Leadership lost: consumer stopped: {}", delegatedEndpoint);
    }

    // **************************************
    // Listener
    // **************************************

    private final class LeadershipListener implements CamelClusterEventListener.Leadership {
        @Override
        public void leadershipChanged(CamelClusterView view, Optional<CamelClusterMember> leader) {
            if (!isRunAllowed()) {
                return;
            }

            try {
                if (view.getLocalMember().isLeader()) {
                    onLeadershipTaken();
                } else if (delegatedConsumer != null) {
                    onLeadershipLost();
                }
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }
}
