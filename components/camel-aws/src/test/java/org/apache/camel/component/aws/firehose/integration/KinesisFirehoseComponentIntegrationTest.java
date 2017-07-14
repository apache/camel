/**
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
package org.apache.camel.component.aws.firehose.integration;

import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehose;
import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehoseAsyncClientBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.firehose.KinesisFirehoseConstants;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class KinesisFirehoseComponentIntegrationTest extends CamelTestSupport {

    @Test
    public void testFirehoseRouting() throws Exception {
        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("my message text");
            }
        });
        assertNotNull(exchange.getIn().getHeader(KinesisFirehoseConstants.RECORD_ID));
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        AmazonKinesisFirehose client = AmazonKinesisFirehoseAsyncClientBuilder.defaultClient();
        JndiRegistry registry = super.createRegistry();
        registry.bind("FirehoseClient", client);
        return registry;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("aws-kinesis-firehose://mystream?amazonKinesisFirehoseClient=#FirehoseClient");
            }
        };
    }
}

