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
package org.apache.camel.component.aw2.timestream.write;

import org.apache.camel.*;
import org.apache.camel.component.aw2.timestream.Timestream2Component;
import org.apache.camel.component.aw2.timestream.Timestream2Configuration;
import org.apache.camel.component.aw2.timestream.Timestream2Constants;
import org.apache.camel.component.aw2.timestream.client.Timestream2ClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteClient;

/**
 * Manage and invoke AWS Timestream.
 */
@UriEndpoint(firstVersion = "4.1.0", scheme = "aws2-timestream", title = "AWS Timestream Write",
             syntax = "aws2-timestream:write:label",
             producerOnly = true, category = { Category.CLOUD, Category.DATABASE },
             headersClass = Timestream2Constants.class)
public class Timestream2WriteEndpoint extends DefaultEndpoint {

    /** AWS TimestreamWriteClient for TimestreamWrite Endpoint **/
    private TimestreamWriteClient awsTimestreamWriteClient;

    @UriParam
    private Timestream2Configuration configuration;

    public Timestream2WriteEndpoint(String uri, Component component, Timestream2Configuration configuration) {
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
        return new Timestream2WriteProducer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        awsTimestreamWriteClient = configuration.getAwsTimestreamWriteClient() != null
                ? configuration.getAwsTimestreamWriteClient()
                : Timestream2ClientFactory.getTimestreamClient(configuration).getTimestreamWriteClient();
    }

    @Override
    public void doStop() throws Exception {

        if (ObjectHelper.isEmpty(configuration.getAwsTimestreamWriteClient())) {
            if (awsTimestreamWriteClient != null) {
                awsTimestreamWriteClient.close();
            }
        }
        super.doStop();
    }

    public Timestream2Configuration getConfiguration() {
        return configuration;
    }

    public TimestreamWriteClient getAwsTimestreamWriteClient() {
        return awsTimestreamWriteClient;
    }

}
