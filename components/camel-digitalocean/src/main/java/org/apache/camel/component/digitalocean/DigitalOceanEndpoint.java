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
package org.apache.camel.component.digitalocean;

import com.myjeeva.digitalocean.impl.DigitalOceanClient;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.digitalocean.producer.DigitalOceanAccountProducer;
import org.apache.camel.component.digitalocean.producer.DigitalOceanActionsProducer;
import org.apache.camel.component.digitalocean.producer.DigitalOceanBlockStoragesProducer;
import org.apache.camel.component.digitalocean.producer.DigitalOceanDropletsProducer;
import org.apache.camel.component.digitalocean.producer.DigitalOceanFloatingIPsProducer;
import org.apache.camel.component.digitalocean.producer.DigitalOceanImagesProducer;
import org.apache.camel.component.digitalocean.producer.DigitalOceanKeysProducer;
import org.apache.camel.component.digitalocean.producer.DigitalOceanRegionsProducer;
import org.apache.camel.component.digitalocean.producer.DigitalOceanSizesProducer;
import org.apache.camel.component.digitalocean.producer.DigitalOceanSnapshotsProducer;
import org.apache.camel.component.digitalocean.producer.DigitalOceanTagsProducer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The DigitalOcean component allows you to manage Droplets and resources within the DigitalOcean cloud.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "digitalocean", title = "DigitalOcean", syntax = "digitalocean:operation", producerOnly = true, label = "cloud,management")
public class DigitalOceanEndpoint extends DefaultEndpoint {

    private static final transient Logger LOG = LoggerFactory.getLogger(DigitalOceanEndpoint.class);

    @UriParam
    private DigitalOceanConfiguration configuration;

    private DigitalOceanClient digitalOceanClient;

    public DigitalOceanEndpoint(String uri, DigitalOceanComponent component, DigitalOceanConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        LOG.trace("Resolve producer digitalocean endpoint {{}}", configuration.getResource());

        switch (configuration.getResource()) {
            case account:
                return new DigitalOceanAccountProducer(this, configuration);
            case actions:
                return new DigitalOceanActionsProducer(this, configuration);
            case blockStorages:
                return new DigitalOceanBlockStoragesProducer(this, configuration);
            case droplets:
                return new DigitalOceanDropletsProducer(this, configuration);
            case images:
                return new DigitalOceanImagesProducer(this, configuration);
            case snapshots:
                return new DigitalOceanSnapshotsProducer(this, configuration);
            case keys:
                return new DigitalOceanKeysProducer(this, configuration);
            case regions:
                return new DigitalOceanRegionsProducer(this, configuration);
            case sizes:
                return new DigitalOceanSizesProducer(this, configuration);
            case floatingIPs:
                return new DigitalOceanFloatingIPsProducer(this, configuration);
            case tags:
                return new DigitalOceanTagsProducer(this, configuration);
            default:
                throw new UnsupportedOperationException("Operation specified is not valid for producer");
        }

    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        if (configuration.getDigitalOceanClient() != null) {
            digitalOceanClient = configuration.getDigitalOceanClient();
        } else if (configuration.getHttpProxyHost() != null && configuration.getHttpProxyPort() != null) {

            HttpClientBuilder builder = HttpClients.custom()
                    .useSystemProperties()
                    .setProxy(new HttpHost(configuration.getHttpProxyHost(), configuration.getHttpProxyPort()));

            if (configuration.getHttpProxyUser() != null && configuration.getHttpProxyPassword() != null) {
                BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                        new AuthScope(configuration.getHttpProxyHost(), configuration.getHttpProxyPort()),
                        new UsernamePasswordCredentials(configuration.getHttpProxyUser(), configuration.getHttpProxyPassword()));
                builder.setDefaultCredentialsProvider(credsProvider);

            }

            digitalOceanClient = new DigitalOceanClient("v2", configuration.getOAuthToken(), builder.build());

        } else {
            digitalOceanClient = new DigitalOceanClient(configuration.getOAuthToken());
        }
    }

    public DigitalOceanConfiguration getConfiguration() {
        return configuration;
    }

    public DigitalOceanClient getDigitalOceanClient() {
        return digitalOceanClient;
    }

}
