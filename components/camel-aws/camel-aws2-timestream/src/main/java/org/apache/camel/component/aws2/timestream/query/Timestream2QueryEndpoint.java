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
package org.apache.camel.component.aws2.timestream.query;

import org.apache.camel.*;
import org.apache.camel.component.aws2.timestream.Timestream2AbstractEndpoint;
import org.apache.camel.component.aws2.timestream.Timestream2Configuration;
import org.apache.camel.component.aws2.timestream.client.Timestream2ClientFactory;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.timestreamquery.TimestreamQueryClient;

/**
 * Manage and invoke AWS Timestream.
 */
public class Timestream2QueryEndpoint extends Timestream2AbstractEndpoint {

    /** AWS TimestreamQueryClient for TimestreamQuery Endpoint **/
    private TimestreamQueryClient awsTimestreamQueryClient;

    public Timestream2QueryEndpoint(String uri, Component component, Timestream2Configuration configuration) {
        super(uri, component, configuration);
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
        awsTimestreamQueryClient = getConfiguration().getAwsTimestreamQueryClient() != null
                ? getConfiguration().getAwsTimestreamQueryClient()
                : Timestream2ClientFactory.getTimestreamClient(getConfiguration()).getTimestreamQueryClient();
    }

    @Override
    public void doStop() throws Exception {

        if (ObjectHelper.isEmpty(getConfiguration().getAwsTimestreamQueryClient())) {
            if (awsTimestreamQueryClient != null) {
                awsTimestreamQueryClient.close();
            }
        }
        super.doStop();
    }

    public TimestreamQueryClient getAwsTimestreamQueryClient() {
        return awsTimestreamQueryClient;
    }

}
