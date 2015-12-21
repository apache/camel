/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder.mllp;

import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mllp.MllpApplicationErrorAcknowledgementException;
import org.apache.camel.component.mllp.MllpApplicationRejectAcknowledgementException;
import org.apache.camel.processor.mllp.Hl7AcknowledgementGenerator;

/**
 * Basic MLLP Receiver
 *
 * NOTE:  This is work-in-progress - I haven't tried it yet at all
 */
public class MllpReceiverBuilder extends RouteBuilder {
    String routeId = "mllp-receiver";

    int listenPort = -1;

    String targetEndpointUri = "direct://mllp-receive";
    String journalEndpointUri = "direct://mllp-journal";
    String errorEndpointUri = "direct://mllp-error";

    @Override
    public void configure() throws Exception {
        validateConfiguration();

        onCompletion()
                .onFailureOnly()
                .to( errorEndpointUri ).id( "error-store")
        ;

        onCompletion()
                .onCompleteOnly()
                .to( journalEndpointUri ).id( "journal-store")
        ;

        fromF( "mllp://%d", listenPort ).id( "mllp-listener")
                .routeId( routeId )
                .to( targetEndpointUri ).id( "persistant-store")
        ;
    }

    /**
     * Validate the configuration
     *
     * @throws Exception when the configuration is invalid
     */
    public void validateConfiguration() throws Exception {
        // TODO: Implement this
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public int getListenPort() {
        return listenPort;
    }

    public void setListenPort(int listenPort) {
        this.listenPort = listenPort;
    }

    public String getTargetEndpointUri() {
        return targetEndpointUri;
    }

    public void setTargetEndpointUri(String targetEndpointUri) {
        this.targetEndpointUri = targetEndpointUri;
    }

    public String getJournalEndpointUri() {
        return journalEndpointUri;
    }

    public void setJournalEndpointUri(String journalEndpointUri) {
        this.journalEndpointUri = journalEndpointUri;
    }

    public String getErrorEndpointUri() {
        return errorEndpointUri;
    }

    public void setErrorEndpointUri(String errorEndpointUri) {
        this.errorEndpointUri = errorEndpointUri;
    }
}
