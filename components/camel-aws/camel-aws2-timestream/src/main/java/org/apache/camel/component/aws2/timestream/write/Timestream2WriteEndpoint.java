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
package org.apache.camel.component.aws2.timestream.write;

import org.apache.camel.*;
import org.apache.camel.component.aws2.timestream.Timestream2AbstractEndpoint;
import org.apache.camel.component.aws2.timestream.Timestream2Configuration;
import org.apache.camel.component.aws2.timestream.client.Timestream2ClientFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteClient;

/**
 * Manage and invoke AWS Timestream.
 */

public class Timestream2WriteEndpoint extends Timestream2AbstractEndpoint implements EndpointServiceLocation {

    /** AWS TimestreamWriteClient for TimestreamWrite Endpoint **/
    private TimestreamWriteClient awsTimestreamWriteClient;

    public Timestream2WriteEndpoint(String uri, Component component, Timestream2Configuration configuration) {
        super(uri, component, configuration);
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
        awsTimestreamWriteClient = getConfiguration().getAwsTimestreamWriteClient() != null
                ? getConfiguration().getAwsTimestreamWriteClient()
                : Timestream2ClientFactory.getTimestreamWriteClient(getConfiguration());
    }

    @Override
    public void doStop() throws Exception {

        if (ObjectHelper.isEmpty(getConfiguration().getAwsTimestreamWriteClient())) {
            if (awsTimestreamWriteClient != null) {
                awsTimestreamWriteClient.close();
            }
        }
        super.doStop();
    }

    public TimestreamWriteClient getAwsTimestreamWriteClient() {
        return awsTimestreamWriteClient;
    }

    @Override
    public String getServiceUrl() {
        if (!getConfiguration().isOverrideEndpoint()) {
            if (ObjectHelper.isNotEmpty(getConfiguration().getRegion())) {
                return getConfiguration().getRegion();
            }
        } else if (ObjectHelper.isNotEmpty(getConfiguration().getUriEndpointOverride())) {
            return getConfiguration().getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "timestream-write";
    }

}
