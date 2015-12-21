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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mllp.MllpApplicationErrorAcknowledgementException;
import org.apache.camel.component.mllp.MllpApplicationRejectAcknowledgementException;
import org.apache.camel.component.mllp.MllpCorruptFrameException;

/**
 * Basic MLLP Sender
 *
 * NOTE:  This is work-in-progress - I haven't tried it yet at all
 */
public class MllpSenderBuilder extends RouteBuilder {
    String routeId = "mllp-sender";

    String sourceEndpointUri = "direct://mllp-feed";
    String journalEndpointUri = "direct://mllp-journal";
    String targetHostname = null;
    int targetPort = -1;

    long redeliveryDelay = 1000;
    int maximumRedeliveries = 5;

    String applicationErrorEndpointUri = "direct://mllp-nack-error";
    String applicationRejectEndpointUri = "direct://mllp-nack-reject";
    String errorEndpointUri = "direct://mllp-error";


    @Override
    public void configure() throws Exception {
        validateConfiguration();

        onCompletion()
                .onFailureOnly()
                .to( errorEndpointUri )
                ;

        onException(MllpApplicationRejectAcknowledgementException.class)
                .handled( true )
                .to( applicationRejectEndpointUri )
        ;

        onException(MllpApplicationErrorAcknowledgementException.class)
                .handled( true )
                .maximumRedeliveries( maximumRedeliveries )
                .redeliveryDelay( redeliveryDelay )
                .to( applicationErrorEndpointUri )
        ;

        from( sourceEndpointUri ).id( "message-source")
                .routeId( routeId )
                .toF("mllp://%s:%d", targetHostname, targetPort ).id( "mllp-client")
                .to( journalEndpointUri )
        ;
    }

    /**
     * Validate the configuration
     *
     * @throws Exception when the configuration is invalid
     */
    public void validateConfiguration() {
        if ( null == targetHostname ) {
            throw new IllegalArgumentException( "MLLP Target Hostname must be set");
        }

        if ( -1 == targetPort ) {
            throw new IllegalArgumentException( "MLLP Target Port must be set");
        }
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getSourceEndpointUri() {
        return sourceEndpointUri;
    }

    public void setSourceEndpointUri(String sourceEndpointUri) {
        this.sourceEndpointUri = sourceEndpointUri;
    }

    public String getJournalEndpointUri() {
        return journalEndpointUri;
    }

    public void setJournalEndpointUri(String journalEndpointUri) {
        this.journalEndpointUri = journalEndpointUri;
    }

    public String getTargetHostname() {
        return targetHostname;
    }

    public void setTargetHostname(String targetHostname) {
        this.targetHostname = targetHostname;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public long getRedeliveryDelay() {
        return redeliveryDelay;
    }

    public void setRedeliveryDelay(long redeliveryDelay) {
        this.redeliveryDelay = redeliveryDelay;
    }

    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    public String getApplicationRejectEndpointUri() {
        return applicationRejectEndpointUri;
    }

    public void setApplicationRejectEndpointUri(String applicationRejectEndpointUri) {
        this.applicationRejectEndpointUri = applicationRejectEndpointUri;
    }

    public String getApplicationErrorEndpointUri() {
        return applicationErrorEndpointUri;
    }

    public void setApplicationErrorEndpointUri(String applicationErrorEndpointUri) {
        this.applicationErrorEndpointUri = applicationErrorEndpointUri;
    }

    public String getErrorEndpointUri() {
        return errorEndpointUri;
    }

    public void setErrorEndpointUri(String errorEndpointUri) {
        this.errorEndpointUri = errorEndpointUri;
    }
}
