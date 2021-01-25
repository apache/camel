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
package org.apache.camel.component.stitch;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.stitch.client.StitchClient;
import org.apache.camel.component.stitch.client.StitchClientBuilder;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Stitch is a cloud ETL service, developer-focused platform for rapidly moving and replicates data from more than 90
 * applications and databases. It integrates various data sources into a central data warehouse. Stitch has integrations
 * for many enterprise software data sources, and can receive data via WebHooks and an API (Stitch Import API) which
 * Camel Stitch Component uses to produce the data to Stitch ETL.
 */
@UriEndpoint(firstVersion = "3.8.0", scheme = "stitch", title = "Stitch",
             syntax = "stitch:tableName", producerOnly = true, category = {
                     Category.CLOUD, Category.API, Category.COMPUTE, Category.BIGDATA })
public class StitchEndpoint extends DefaultEndpoint {

    @UriParam
    private StitchConfiguration configuration = new StitchConfiguration();

    private StitchClient stitchClient;

    public StitchEndpoint() {
    }

    public StitchEndpoint(final String uri, final Component component, final StitchConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (stitchClient == null) {
            stitchClient
                    = configuration.getStitchClient() != null ? configuration.getStitchClient() : createClient(configuration);
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        return new StitchProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Stitch component does not support consumer operations.");
    }

    /**
     * The component configurations
     */
    public StitchConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(StitchConfiguration configuration) {
        this.configuration = configuration;
    }

    public StitchClient getStitchClient() {
        return stitchClient;
    }

    public void setStitchClient(StitchClient stitchClient) {
        this.stitchClient = stitchClient;
    }

    private StitchClient createClient(final StitchConfiguration configuration) {
        return StitchClientBuilder.builder()
                .withRegion(getConfiguration().getRegion())
                .withToken(getConfiguration().getToken())
                .withHttpClient(getConfiguration().getHttpClient())
                .withConnectionProvider(getConfiguration().getConnectionProvider())
                .build();
    }
}
