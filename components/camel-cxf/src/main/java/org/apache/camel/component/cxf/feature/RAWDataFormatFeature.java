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
package org.apache.camel.component.cxf.feature;

import org.apache.camel.component.cxf.interceptors.OneWayOutgoingChainInterceptor;
import org.apache.camel.component.cxf.interceptors.RawMessageContentRedirectInterceptor;
import org.apache.camel.component.cxf.interceptors.RawMessageWSDLGetInterceptor;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.interceptor.OneWayProcessorInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * MessageDataFormatFeature sets up the CXF endpoint interceptor for handling the
 * Message in Message data format.  Only the interceptors of these phases are
 * <b>preserved</b>:
 * </p>
 * <p>
 * In phases: {Phase.RECEIVE , Phase.INVOKE, Phase.POST_INVOKE}
 * </p>
 * <p>
 * Out phases: {Phase.PREPARE_SEND, Phase.WRITE, Phase.SEND, Phase.PREPARE_SEND_ENDING}
 * </p>
 */
public class RAWDataFormatFeature extends AbstractDataFormatFeature {
    private static final Logger LOG = LoggerFactory.getLogger(RAWDataFormatFeature.class);

    // filter the unused in phase interceptor
    private static final String[] REMAINING_IN_PHASES = {Phase.RECEIVE, Phase.USER_STREAM,
        Phase.INVOKE, Phase.POST_INVOKE};
    // filter the unused in phase interceptor
    private static final String[] REMAINING_OUT_PHASES = {Phase.PREPARE_SEND, Phase.USER_STREAM,
        Phase.WRITE, Phase.SEND, Phase.PREPARE_SEND_ENDING};
    
    private boolean oneway;

    @Override
    public void initialize(Client client, Bus bus) {
        //check if there is logging interceptor
        removeInterceptorWhichIsOutThePhases(client.getInInterceptors(), REMAINING_IN_PHASES, getInInterceptorNames());
        removeInterceptorWhichIsOutThePhases(client.getEndpoint().getInInterceptors(), REMAINING_IN_PHASES, getInInterceptorNames());
        client.getEndpoint().getBinding().getInInterceptors().clear();

        //we need to keep the LoggingOutputInterceptor
        getOutInterceptorNames().add(LoggingOutInterceptor.class.getName());
        removeInterceptorWhichIsOutThePhases(client.getOutInterceptors(), REMAINING_OUT_PHASES, getOutInterceptorNames());
        removeInterceptorWhichIsOutThePhases(client.getEndpoint().getOutInterceptors(), REMAINING_OUT_PHASES, getOutInterceptorNames());
        client.getEndpoint().getBinding().getOutInterceptors().clear();
        client.getEndpoint().getOutInterceptors().add(new RawMessageContentRedirectInterceptor());
    }

    @Override
    public void initialize(Server server, Bus bus) {
        // currently we do not filter the bus
        // remove the interceptors

        removeInterceptorWhichIsOutThePhases(server.getEndpoint().getService().getInInterceptors(), REMAINING_IN_PHASES, getInInterceptorNames());
        removeInterceptorWhichIsOutThePhases(server.getEndpoint().getInInterceptors(), REMAINING_IN_PHASES, getInInterceptorNames());

        
        //we need to keep the LoggingOutputInterceptor
        getOutInterceptorNames().add(LoggingOutInterceptor.class.getName());
        
        // Do not using the binding interceptor any more
        server.getEndpoint().getBinding().getInInterceptors().clear();

        removeInterceptorWhichIsOutThePhases(server.getEndpoint().getService().getOutInterceptors(), REMAINING_OUT_PHASES, getOutInterceptorNames());
        removeInterceptorWhichIsOutThePhases(server.getEndpoint().getOutInterceptors(), REMAINING_OUT_PHASES, getOutInterceptorNames());

        // Do not use the binding interceptor any more
        server.getEndpoint().getBinding().getOutInterceptors().clear();
        server.getEndpoint().getOutInterceptors().add(new RawMessageContentRedirectInterceptor());

        // setup the RawMessageWSDLGetInterceptor
        server.getEndpoint().getInInterceptors().add(RawMessageWSDLGetInterceptor.INSTANCE);
        // Oneway with RAW message
        if (isOneway()) {
            Interceptor<? extends Message> toRemove = null;
            for (Interceptor<? extends Message> i : server.getEndpoint().getService().getInInterceptors()) {
                if (i.getClass().getName().equals("org.apache.cxf.interceptor.OutgoingChainInterceptor")) {
                    toRemove = i;
                }
            }
            server.getEndpoint().getService().getInInterceptors().remove(toRemove);
            server.getEndpoint().getInInterceptors().add(new OneWayOutgoingChainInterceptor());
            server.getEndpoint().getInInterceptors().add(new OneWayProcessorInterceptor());
        }
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    public boolean isOneway() {
        return oneway;
    }

    public void setOneway(boolean oneway) {
        this.oneway = oneway;
    }
   

}
