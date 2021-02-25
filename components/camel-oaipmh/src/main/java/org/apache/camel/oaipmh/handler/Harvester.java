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
package org.apache.camel.oaipmh.handler;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.apache.camel.oaipmh.component.model.OAIPMHVerb;
import org.apache.camel.oaipmh.model.OAIPMHResponse;
import org.apache.camel.oaipmh.utils.OAIPMHHttpClient;

public class Harvester {

    private static final String NO_TOKEN = null;
    private String resumptionToken = NO_TOKEN;
    private URI baseURI;
    private String verb;
    private String metadata;
    private String until;
    private String from;
    private String set;
    private String identifier;
    private OAIPMHHttpClient httpClient;
    private ResponseHandler oaipmhResponseHandler;

    private boolean empty;

    public Harvester(ResponseHandler oaipmhResponseHandler, URI baseURI, String verb, String metadata, String until,
                     String from, String set, String identifier) {
        this.baseURI = baseURI;
        this.verb = verb;
        this.metadata = metadata;
        this.until = until;
        this.from = from;
        this.set = set;
        this.identifier = identifier;
        this.httpClient = new OAIPMHHttpClient();
        this.oaipmhResponseHandler = oaipmhResponseHandler;

        if (OAIPMHVerb.valueOf(verb) == OAIPMHVerb.ListMetadataFormats || OAIPMHVerb.valueOf(verb) == OAIPMHVerb.ListSets
                || OAIPMHVerb.valueOf(verb) == OAIPMHVerb.Identify) {
            this.metadata = null;
            this.until = null;
            this.from = null;
            this.set = null;
            this.identifier = null;
        }

    }

    private boolean harvest() throws Exception {
        boolean hasNext = false;
        if (!this.empty) {
            String responseXML = httpClient.doRequest(this.baseURI, this.verb, this.set, this.from, this.until, this.metadata,
                    this.resumptionToken, this.identifier);
            OAIPMHResponse oaipmhResponse = new OAIPMHResponse(responseXML);
            this.oaipmhResponseHandler.process(oaipmhResponse);
            Optional<String> resumptionToken = oaipmhResponse.getResumptionToken();
            if (resumptionToken.isPresent() && !resumptionToken.get().isEmpty()) {
                this.resumptionToken = resumptionToken.get();
                hasNext = true;
            } else {
                this.resumptionToken = null;
                this.empty = true;
            }
        }
        return hasNext;
    }

    public void asynHarvest() throws Exception {
        this.harvest();
    }

    public List<String> synHarvest(boolean onlyFirst) throws Exception {
        while (this.harvest()) {
            if (onlyFirst) {
                break;
            }
        }
        return this.oaipmhResponseHandler.flush();
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public String getResumptionToken() {
        return resumptionToken;
    }

    public void setResumptionToken(String resumptionToken) {
        this.resumptionToken = resumptionToken;
    }

    public URI getBaseURI() {
        return baseURI;
    }

    public void setBaseURI(URI baseURI) {
        this.baseURI = baseURI;
    }

    public String getVerb() {
        return verb;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getUntil() {
        return until;
    }

    public void setUntil(String until) {
        this.until = until;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSet() {
        return set;
    }

    public void setSet(String set) {
        this.set = set;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public OAIPMHHttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(OAIPMHHttpClient httpClient) {
        this.httpClient = httpClient;
    }

}
