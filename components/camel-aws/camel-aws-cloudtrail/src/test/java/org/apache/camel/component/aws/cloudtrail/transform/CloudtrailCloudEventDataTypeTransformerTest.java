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
package org.apache.camel.component.aws.cloudtrail.transform;

import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.aws.cloudtrail.CloudtrailConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CloudtrailCloudEventDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final CloudtrailCloudEventDataTypeTransformer transformer = new CloudtrailCloudEventDataTypeTransformer();

    @Test
    void shouldMapToCloudEventWithJsonContentType() {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(CloudtrailConstants.EVENT_ID, "1234");
        exchange.getMessage().setHeader(CloudtrailConstants.EVENT_SOURCE, "s3.amazonaws.com");
        exchange.getMessage().setBody("{\"eventVersion\":\"1.08\"}".getBytes(StandardCharsets.UTF_8));

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals("org.apache.camel.event.aws.cloudtrail.lookupEvents",
                exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        Assertions.assertEquals("1234", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
        Assertions.assertEquals("aws.cloudtrail.event.source.s3.amazonaws.com",
                exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
        // the body is the JSON of the CloudTrail record
        Assertions.assertEquals("application/json",
                exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE));
    }
}
