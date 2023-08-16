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
package org.apache.camel.component.aw2.timestream.query;

import org.apache.camel.*;
import org.apache.camel.component.aw2.timestream.Timestream2Component;
import org.apache.camel.component.aw2.timestream.Timestream2Configuration;
import org.apache.camel.component.aw2.timestream.Timestream2Constants;
import org.apache.camel.component.aw2.timestream.client.Timestream2ClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.timestreamquery.TimestreamQueryClient;

/**
 * Manage and invoke AWS Timestream.
 */
@UriEndpoint(firstVersion = "4.1.0", scheme = "aws2-timestream", title = "AWS Timestream Query",
             syntax = "aws2-timestream:query:label",
             producerOnly = true, category = { Category.CLOUD, Category.DATABASE },
             headersClass = Timestream2Constants.class)
public class Timestream2QueryEndpoint extends DefaultEndpoint {

    /** AWS TimestreamQueryClient for TimestreamQuery Endpoint **/
    private TimestreamQueryClient awsTimestreamQueryClient;

    @UriParam
    private Timestream2Configuration configuration;

    public Timestream2QueryEndpoint(String uri, Component component, Timestream2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Timestream2Component getComponent() {
        return (Timestream2Component) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Timestream2QueryProducer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        awsTimestreamQueryClient = configuration.getAwsTimestreamQueryClient() != null
                ? configuration.getAwsTimestreamQueryClient()
                : Timestream2ClientFactory.getTimestreamClient(configuration).getTimestreamQueryClient();
    }

    @Override
    public void doStop() throws Exception {

        if (ObjectHelper.isEmpty(configuration.getAwsTimestreamQueryClient())) {
            if (awsTimestreamQueryClient != null) {
                awsTimestreamQueryClient.close();
            }
        }
        super.doStop();
    }

    public Timestream2Configuration getConfiguration() {
        return configuration;
    }

    public TimestreamQueryClient getAwsTimestreamQueryClient() {
        return awsTimestreamQueryClient;
    }

}
