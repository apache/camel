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
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.oaipmh.handler.Harvester;
import org.apache.camel.oaipmh.handler.ProducerResponseHandler;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The OAIPMH producer.
 */
public class OAIPMHProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OAIPMHProducer.class);
    private OAIPMHEndpoint endpoint;

    public OAIPMHProducer(OAIPMHEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Harvester harvester = new Harvester(
                new ProducerResponseHandler(),
                endpoint.getUrl(),
                endpoint.getVerb(),
                endpoint.getMetadataPrefix(),
                endpoint.getUntil(),
                endpoint.getFrom(),
                endpoint.getSet(),
                endpoint.getIdentitier());
        overrideHarvesterConfigs(exchange.getIn(), harvester);
        List<String> synHarvest = harvester.synHarvest(endpoint.isOnlyFirst());
        exchange.getMessage().setBody(synHarvest);
        if (endpoint.isOnlyFirst() && harvester.getResumptionToken() != null) {
            exchange.getMessage().setHeader("CamelOaimphResumptionToken", harvester.getResumptionToken());
        } else {
            exchange.getMessage().removeHeader("CamelOaimphResumptionToken");
        }
    }

    private void overrideHarvesterConfigs(Message msg, Harvester harvester) {
        String header = msg.getHeader("CamelOaimphUrl", String.class);
        if (header != null) {
            harvester.setBaseURI(URI.create(header));
        }

        header = msg.getHeader("CamelOaimphEndpointUrl", String.class);
        if (header != null) {
            harvester.setBaseURI(URI.create(header));
        }

        header = msg.getHeader("CamelOaimphVerb", String.class);
        if (header != null) {
            harvester.setVerb(header);
        }
        header = msg.getHeader("CamelOaimphMetadataPrefix", String.class);
        if (header != null) {
            harvester.setMetadata(header);
        }
        header = msg.getHeader("CamelOaimphUntil", String.class);
        if (header != null) {
            harvester.setUntil(header);
        }
        header = msg.getHeader("CamelOaimphFrom", String.class);
        if (header != null) {
            harvester.setFrom(header);
        }
        header = msg.getHeader("CamelOaimphSet", String.class);
        if (header != null) {
            harvester.setSet(header);
        }
        header = msg.getHeader("CamelOaimphIdentifier", String.class);
        if (header != null) {
            harvester.setIdentifier(header);
        }
        header = msg.getHeader("CamelOaimphResumptionToken", String.class);
        if (header != null) {
            harvester.setResumptionToken(header);
        }
        Boolean headerBoolean = msg.getHeader("CamelOaimphOnlyFirst", Boolean.class);
        if (headerBoolean != null) {
            endpoint.setOnlyFirst(headerBoolean);
        }
    }

}
