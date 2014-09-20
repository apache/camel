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
package org.apache.camel.component.openshift;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.openshift.client.IApplication;
import com.openshift.client.IDomain;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;

public class OpenShiftConsumer extends ScheduledPollConsumer {

    // lets by default poll every 10 sec
    private static final long INITIAL_DELAY = 1 * 1000L;
    private static final long DELAY = 10 * 1000L;

    private final Map<ApplicationState, ApplicationState> oldState = new HashMap<ApplicationState, ApplicationState>();
    private volatile boolean initialPoll;

    public OpenShiftConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        setInitialDelay(INITIAL_DELAY);
        setDelay(DELAY);
    }

    @Override
    public OpenShiftEndpoint getEndpoint() {
        return (OpenShiftEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        initialPoll = true;
        super.doStart();
    }

    @Override
    protected int poll() throws Exception {
        String openshiftServer = OpenShiftHelper.getOpenShiftServer(getEndpoint());
        IDomain domain = OpenShiftHelper.loginAndGetDomain(getEndpoint(), openshiftServer);
        if (domain == null) {
            return 0;
        }

        return doPollOnChange(domain);
    }

    protected int doPollAll(IDomain domain) {
        List<IApplication> apps = domain.getApplications();
        for (IApplication app : apps) {
            Exchange exchange = getEndpoint().createExchange(app);
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error during processing exchange.", exchange, exchange.getException());
            }
        }
        return apps.size();
    }

    protected int doPollOnChange(IDomain domain) {

        // an app can either be
        // - added
        // - removed
        // - state changed

        Map<ApplicationState, ApplicationState> newState = new HashMap<ApplicationState, ApplicationState>();

        List<IApplication> apps = domain.getApplications();
        for (IApplication app : apps) {
            ApplicationState state = new ApplicationState(app.getUUID(), app, OpenShiftHelper.getStateForApplication(app));
            newState.put(state, state);
        }

        // compute what is the delta from last time
        // so we split up into 3 groups, of added/removed/changed
        Map<ApplicationState, ApplicationState> added = new HashMap<ApplicationState, ApplicationState>();
        Map<ApplicationState, ApplicationState> removed = new HashMap<ApplicationState, ApplicationState>();
        Map<ApplicationState, ApplicationState> changed = new HashMap<ApplicationState, ApplicationState>();

        for (ApplicationState state : newState.keySet()) {
            if (!oldState.containsKey(state)) {
                // its a new app added
                added.put(state, state);
            } else {
                ApplicationState old = oldState.get(state);
                if (old != null && !old.getState().equals(state.getState())) {
                    // the state changed
                    state.setOldState(old.getState());
                    changed.put(state, state);
                }
            }
        }
        for (ApplicationState state : oldState.keySet()) {
            if (!newState.containsKey(state)) {
                // its a app removed
                removed.put(state, state);
            }
        }

        // only start emitting events after first init poll
        int processed = 0;
        if (!initialPoll) {

            for (ApplicationState add : added.keySet()) {
                Exchange exchange = getEndpoint().createExchange(add.getApplication());
                exchange.getIn().setHeader(OpenShiftConstants.EVENT_TYPE, "added");
                try {
                    processed++;
                    getProcessor().process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error during processing exchange.", exchange, exchange.getException());
                }
            }
            for (ApplicationState remove : removed.keySet()) {
                Exchange exchange = getEndpoint().createExchange(remove.getApplication());
                exchange.getIn().setHeader(OpenShiftConstants.EVENT_TYPE, "removed");
                try {
                    processed++;
                    getProcessor().process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error during processing exchange.", exchange, exchange.getException());
                }
            }

            for (ApplicationState change : changed.keySet()) {
                Exchange exchange = getEndpoint().createExchange(change.getApplication());
                exchange.getIn().setHeader(OpenShiftConstants.EVENT_TYPE, "changed");
                exchange.getIn().setHeader(OpenShiftConstants.EVENT_OLD_STATE, change.getOldState());
                exchange.getIn().setHeader(OpenShiftConstants.EVENT_NEW_STATE, change.getState());
                try {
                    processed++;
                    getProcessor().process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error during processing exchange.", exchange, exchange.getException());
                }
            }
        }

        // update old state with latest state for next poll
        oldState.clear();
        oldState.putAll(newState);

        initialPoll = false;

        return processed;
    }

    private static final class ApplicationState {
        private final String uuid;
        private final IApplication application;
        private final String state;
        private String oldState;

        private ApplicationState(String uuid, IApplication application, String state) {
            this.uuid = uuid;
            this.application = application;
            this.state = state;
        }

        public String getUuid() {
            return uuid;
        }

        public IApplication getApplication() {
            return application;
        }

        public String getState() {
            return state;
        }

        public String getOldState() {
            return oldState;
        }

        public void setOldState(String oldState) {
            this.oldState = oldState;
        }

        // only use uuid and state for equals as that is what we want to use for state change detection

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ApplicationState that = (ApplicationState) o;

            if (!state.equals(that.state)) {
                return false;
            }
            if (!uuid.equals(that.uuid)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = uuid.hashCode();
            result = 31 * result + state.hashCode();
            return result;
        }
    }

}
