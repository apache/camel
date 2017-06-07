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
package org.apache.camel.component.master;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.StartupListener;
import org.apache.camel.SuspendableService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.ha.CamelClusterEventListener;
import org.apache.camel.ha.CamelClusterMember;
import org.apache.camel.ha.CamelClusterService;
import org.apache.camel.ha.CamelClusterView;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed Master Consumer")
public class MasterConsumer extends DefaultConsumer {
    private static final transient Logger LOGER = LoggerFactory.getLogger(MasterConsumer.class);

    private final MasterEndpoint masterEndpoint;
    private final Endpoint delegatedEndpoint;
    private final Processor processor;
    private final CamelClusterEventListener.Leadership leadershipListener;
    private Consumer delegatedConsumer;
    private CamelClusterService service;
    private CamelClusterView view;

    public MasterConsumer(MasterEndpoint masterEndpoint, Processor processor) {
        super(masterEndpoint, processor);

        this.masterEndpoint = masterEndpoint;
        this.delegatedEndpoint = masterEndpoint.getEndpoint();
        this.processor = processor;
        this.leadershipListener = new LeadershipListener();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        CamelContext context = super.getEndpoint().getCamelContext();
        service = context.hasService(CamelClusterService.class);

        if (service == null) {
            throw new IllegalStateException("No cluster service found");
        }

        view = service.getView(masterEndpoint.getNamespace());
        view.addEventListener(leadershipListener);

        if (isMaster()) {
            onLeadershipTaken();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (view != null) {
            view.removeEventListener(leadershipListener);
        }

        ServiceHelper.stopAndShutdownServices(delegatedConsumer);
        ServiceHelper.stopAndShutdownServices(delegatedEndpoint);

        delegatedConsumer = null;
    }

    @Override
    protected void doResume() throws Exception {
        if (delegatedConsumer != null && delegatedConsumer instanceof SuspendableService) {
            ((SuspendableService)delegatedConsumer).resume();
        }
        super.doResume();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (delegatedConsumer != null && delegatedConsumer instanceof SuspendableService) {
            ((SuspendableService)delegatedConsumer).suspend();
        }
        super.doSuspend();
    }

    @ManagedAttribute(description = "Are we the master")
    public boolean isMaster() {
        return view != null
            ? view.getLocalMember().isMaster()
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

        LOGER.info("Leadership taken: consumer started: {}", delegatedEndpoint);
    }

    private synchronized void onLeadershipLost() throws Exception {
        ServiceHelper.stopAndShutdownServices(delegatedConsumer);
        ServiceHelper.stopAndShutdownServices(delegatedEndpoint);

        delegatedConsumer = null;

        LOGER.info("Leadership lost: consumer stopped: {}", delegatedEndpoint);
    }

    // **************************************
    // Listener
    // **************************************

    private final class LeadershipListener implements CamelClusterEventListener.Leadership {
        @Override
        public void leadershipChanged(CamelClusterView view, CamelClusterMember leader) {
            if (!isRunAllowed()) {
                return;
            }

            try {
                if (view.getLocalMember().isMaster()) {
                    onLeadershipTaken();
                } else if (delegatedConsumer != null) {
                    onLeadershipLost();
                }
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
    }
}
