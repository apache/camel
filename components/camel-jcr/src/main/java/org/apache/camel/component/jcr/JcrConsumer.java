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
package org.apache.camel.component.jcr;

import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.EventListener;

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.Consumer} to consume JCR events.
 *
 * @version $Id$
 */
public class JcrConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(JcrConsumer.class);

    private Session session;
    private EventListener eventListener;
    private ScheduledFuture<?> sessionListenerCheckerScheduledFuture;

    public JcrConsumer(JcrEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        scheduleSessionListenerChecker();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        cancelSessionListenerChecker();
        unregisterListenerAndLogoutSession();
    }

    protected JcrEndpoint getJcrEndpoint() {
        JcrEndpoint endpoint = (JcrEndpoint) getEndpoint();
        return endpoint;
    }

    private synchronized void createSessionAndRegisterListener() throws RepositoryException {
        LOG.trace("createSessionAndRegisterListener START");

        if (ObjectHelper.isEmpty(getJcrEndpoint().getWorkspaceName())) { 
            session = getJcrEndpoint().getRepository().login(getJcrEndpoint().getCredentials());
        } else {
            session = getJcrEndpoint().getRepository().login(getJcrEndpoint().getCredentials(), getJcrEndpoint().getWorkspaceName());
        }

        int eventTypes = getJcrEndpoint().getEventTypes();
        String absPath = getJcrEndpoint().getBase();

        if (absPath == null) {
            absPath = "/";
        } else if (!absPath.startsWith("/")) {
            absPath = "/" + absPath;
        }

        boolean isDeep = getJcrEndpoint().isDeep();
        String[] uuid = null;
        String uuids = getJcrEndpoint().getUuids();

        if (uuids != null) {
            uuids = uuids.trim();

            if (!"".equals(uuids)) {
                uuid = uuids.split(",");
            }
        }

        String[] nodeTypeName = null;
        String nodeTypeNames = getJcrEndpoint().getNodeTypeNames();

        if (nodeTypeNames != null) {
            nodeTypeNames = nodeTypeNames.trim();

            if (!"".equals(nodeTypeNames)) {
                nodeTypeName = nodeTypeNames.split(",");
            }
        }

        boolean noLocal = getJcrEndpoint().isNoLocal();

        eventListener = new EndpointEventListener(getJcrEndpoint(), getProcessor());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding JCR Event Listener, {}, on {}. eventTypes=" + eventTypes + ", isDeep=" + isDeep
                    + ", uuid=" + Arrays.toString(uuid) + ", nodeTypeName=" + Arrays.toString(nodeTypeName) + ", noLocal=" + noLocal, eventListener,
                    absPath);
        }

        session.getWorkspace().getObservationManager()
                .addEventListener(eventListener, eventTypes, absPath, isDeep, uuid, nodeTypeName, noLocal);

        LOG.trace("createSessionAndRegisterListener END");
    }

    private synchronized void unregisterListenerAndLogoutSession() throws RepositoryException {
        LOG.trace("unregisterListenerAndLogoutSession START");

        if (session != null) {
            try {
                if (!session.isLive()) {
                    LOG.info("Session was is no more live.");
                } else {
                    if (eventListener != null) {
                        session.getWorkspace().getObservationManager().removeEventListener(eventListener);
                        eventListener = null;
                    }

                    session.logout();
                }
            } finally {
                eventListener = null;
                session = null;
            }
        }

        LOG.trace("unregisterListenerAndLogoutSession END");
    }

    private void cancelSessionListenerChecker() {
        if (sessionListenerCheckerScheduledFuture != null) {
            sessionListenerCheckerScheduledFuture.cancel(true);
        }
    }

    private void scheduleSessionListenerChecker() {
        String name = "JcrConsumerSessionChecker[" + getJcrEndpoint().getEndpointConfiguredDestinationName() + "]";
        ScheduledExecutorService executor = getJcrEndpoint().getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(this, name);
        JcrConsumerSessionListenerChecker sessionListenerChecker = new JcrConsumerSessionListenerChecker();
        long sessionLiveCheckIntervalOnStart = JcrConsumer.this.getJcrEndpoint().getSessionLiveCheckIntervalOnStart();
        long sessionLiveCheckInterval = JcrConsumer.this.getJcrEndpoint().getSessionLiveCheckInterval();
        sessionListenerCheckerScheduledFuture = executor.scheduleWithFixedDelay(sessionListenerChecker,
                sessionLiveCheckIntervalOnStart, sessionLiveCheckInterval, TimeUnit.MILLISECONDS);
    }

    private class JcrConsumerSessionListenerChecker implements Runnable {

        public void run() {
            LOG.debug("JcrConsumerSessionListenerChecker starts.");

            boolean isSessionLive = false;

            synchronized (this) {
                if (JcrConsumer.this.session != null) {
                    try {
                        isSessionLive = JcrConsumer.this.session.isLive();
                    } catch (Exception e) {
                        LOG.debug("Exception while checking jcr session", e);
                    }
                }
            }

            if (!isSessionLive) {
                try {
                    createSessionAndRegisterListener();
                } catch (RepositoryException e) {
                    LOG.error("Failed to create session and register listener", e);
                }
            }

            LOG.debug("JcrConsumerSessionListenerChecker stops.");
        }
    }

}
