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
package org.apache.camel.oaipmh.component;

import java.net.URI;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultPollingEndpoint;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Represents a OAIPMH endpoint. Component for do OAI-PMH request
 */
@UriEndpoint(firstVersion = "3.5.0", scheme = "oaipmh", title = "OAIPMH", syntax = "oaipmh:name", category = {Category.ENDPOINT, Category.WEBSERVICE, Category.BATCH})
public class OAIPMHEndpoint extends DefaultPollingEndpoint {

    @UriPath(description = "Base URL of the repository to which the request is made through the OAI-PMH protocol.", label = "advanced")
    @Metadata(required = true)
    private URI url;

    @UriParam(description = "Base URL of the repository to which the request is made through the OAI-PMH protocol. (Parameter)")
    private String endpointUrl;

    @UriParam(description = "Specifies a lower bound for datestamp-based selective harvesting. UTC DateTime value")
    private String from;

    @UriParam(description = "Specifies an upper bound for datestamp-based selective harvesting. UTC DateTime value.")
    private String until;

    @UriParam(description = "Specifies membership as a criteria for set-based selective harvesting.")
    private String set;

    @UriParam(description = "Request name supported by OAI-PMh protocol", defaultValue = "ListRecords")
    private String verb = "ListRecords";

    @UriParam(description = "Specifies the metadataPrefix of the format that should be included in the metadata part of the returned records.", defaultValue = "oai_dc")
    private String metadataPrefix = "oai_dc";

    @UriParam(description = "Causes the defined url to make an https request", defaultValue = "false")
    private boolean ssl;

    @UriParam(description = "Identifier of the requested resources. Applicable only with certain verbs ")
    private String identitier;

    @UriParam(description = "Returns the response of a single request. Otherwise it will make requests until there is no more data to return.", defaultValue = "False")
    private boolean onlyFirst;

    public OAIPMHEndpoint(String uri, String url, OAIPMHComponent component) {
        super(uri, component);
        if (this.getEndpointUrl() != null) {
            //this.url = this.getEndpointUrl() == null ? null : URI.create(this.getEndpointUrl());
            this.url = URI.create(this.getEndpointUrl());
        } else {
            this.url = url.isEmpty() ? null : URI.create((this.isSsl() ? "https://" : "http://") + url);
        }
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.url = URI.create(endpointUrl);
        this.endpointUrl = endpointUrl;
    }

    public Producer createProducer() throws Exception {
        return new OAIPMHProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        OAIPMHConsumer consumer = new OAIPMHConsumer(this, processor);
        validateParameters();
        configureConsumer(consumer);
        return consumer;
    }

    private void validateParameters() {
        // From parameter in ISO 8601 format
        if (from != null) {
            ISODateTimeFormat.dateTimeNoMillis().parseDateTime(from);
        }
        if (until != null) {
            ISODateTimeFormat.dateTimeNoMillis().parseDateTime(until);
        }
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public boolean isSingleton() {
        return true;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getUntil() {
        return until;
    }

    public void setUntil(String until) {
        this.until = until;
    }

    public String getSet() {
        return set;
    }

    public void setSet(String set) {
        this.set = set;
    }

    public String getVerb() {
        return verb;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }

    public String getMetadataPrefix() {
        return metadataPrefix;
    }

    public void setMetadataPrefix(String metadataPrefix) {
        this.metadataPrefix = metadataPrefix;
    }

    public URI getUrl() {
        return this.url;
    }

    public String getIdentitier() {
        return identitier;
    }

    public void setIdentitier(String identitier) {
        this.identitier = identitier;
    }

    public boolean isOnlyFirst() {
        return onlyFirst;
    }

    public void setOnlyFirst(boolean onlyFist) {
        this.onlyFirst = onlyFist;
    }

}
