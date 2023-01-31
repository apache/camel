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
import java.util.function.Consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.oaipmh.component.model.OAIPMHConstants;
import org.apache.camel.oaipmh.handler.Harvester;
import org.apache.camel.oaipmh.handler.ProducerResponseHandler;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * The OAIPMH producer.
 */
public class OAIPMHProducer extends DefaultProducer {
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
                endpoint.getIdentifier());
        overrideHarvesterConfigs(exchange.getIn(), harvester);
        if (endpoint.isIgnoreSSLWarnings()) {
            harvester.getHttpClient().setIgnoreSSLWarnings(true);
        }
        List<String> synHarvest = harvester.synHarvest(endpoint.isOnlyFirst());
        exchange.getMessage().setBody(synHarvest);
        if (endpoint.isOnlyFirst() && harvester.getResumptionToken() != null) {
            exchange.getMessage().setHeader(OAIPMHConstants.RESUMPTION_TOKEN, harvester.getResumptionToken());
        } else {
            exchange.getMessage().removeHeader(OAIPMHConstants.RESUMPTION_TOKEN);
        }
    }

    private void overrideHarvesterConfigs(Message msg, Harvester harvester) {
        checkAndSetConfigs(msg, OAIPMHConstants.URL, x -> harvester.setBaseURI(URI.create(x)), String.class);
        checkAndSetConfigs(msg, OAIPMHConstants.ENDPOINT_URL, x -> harvester.setBaseURI(URI.create(x)), String.class);
        checkAndSetConfigs(msg, OAIPMHConstants.VERB, harvester::setVerb, String.class);
        checkAndSetConfigs(msg, OAIPMHConstants.METADATA_PREFIX, harvester::setMetadata, String.class);
        checkAndSetConfigs(msg, OAIPMHConstants.UNTIL, harvester::setUntil, String.class);
        checkAndSetConfigs(msg, OAIPMHConstants.FROM, harvester::setFrom, String.class);
        checkAndSetConfigs(msg, OAIPMHConstants.SET, harvester::setSet, String.class);
        checkAndSetConfigs(msg, OAIPMHConstants.IDENTIFIER, harvester::setIdentifier, String.class);
        checkAndSetConfigs(msg, OAIPMHConstants.RESUMPTION_TOKEN, harvester::setResumptionToken, String.class);
        checkAndSetConfigs(msg, OAIPMHConstants.ONLY_FIRST, endpoint::setOnlyFirst, Boolean.class);
        checkAndSetConfigs(msg, OAIPMHConstants.IGNORE_SSL_WARNINGS, endpoint::setIgnoreSSLWarnings, Boolean.class);
    }

    private <T> void checkAndSetConfigs(final Message message, final String key, final Consumer<T> fn, final Class<T> type) {
        final T header = message.getHeader(key, type);
        if (!ObjectHelper.isEmpty(header)) {
            fn.accept(header);
        }
    }

}
