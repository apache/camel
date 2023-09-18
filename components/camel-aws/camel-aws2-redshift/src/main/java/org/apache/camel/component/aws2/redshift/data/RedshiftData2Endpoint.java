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
package org.apache.camel.component.aws2.redshift.data;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.redshift.data.client.RedshiftData2ClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.redshiftdata.RedshiftDataClient;

/**
 * Perform operations on AWS Redshift using Redshift Data API.
 */
@UriEndpoint(firstVersion = "4.1.0", scheme = "aws2-redshift-data", title = "AWS RedshiftData",
             syntax = "aws2-redshift-data:label",
             producerOnly = true, category = {
                     Category.CLOUD, Category.SERVERLESS,
                     Category.DATABASE, Category.BIGDATA },
             headersClass = RedshiftData2Constants.class)
public class RedshiftData2Endpoint extends DefaultEndpoint {

    private RedshiftDataClient awsRedshiftDataClient;

    @UriParam
    private RedshiftData2Configuration configuration;

    public RedshiftData2Endpoint(String uri, Component component, RedshiftData2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public RedshiftData2Component getComponent() {
        return (RedshiftData2Component) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new RedshiftData2Producer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        awsRedshiftDataClient = configuration.getAwsRedshiftDataClient() != null
                ? configuration.getAwsRedshiftDataClient()
                : RedshiftData2ClientFactory.getRedshiftDataClient(configuration).getRedshiftDataClient();
    }

    @Override
    public void doStop() throws Exception {

        if (ObjectHelper.isEmpty(configuration.getAwsRedshiftDataClient())) {
            if (awsRedshiftDataClient != null) {
                awsRedshiftDataClient.close();
            }
        }
        super.doStop();
    }

    public RedshiftData2Configuration getConfiguration() {
        return configuration;
    }

    public RedshiftDataClient getAwsRedshiftDataClient() {
        return awsRedshiftDataClient;
    }

}
