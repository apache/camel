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

package org.apache.camel.component.aws2.s3.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AWS2S3TransformCloudEventsTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final AWS2S3CloudEventDataTypeTransformer transformer = new AWS2S3CloudEventDataTypeTransformer();

    @Test
    void shouldMapToCloudEvent() {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setHeader(AWS2S3Constants.BUCKET_NAME, "mycamel");
        exchange.getMessage().setHeader(AWS2S3Constants.KEY, "camel.txt");
        exchange.getMessage().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.getObject);
        exchange.getMessage().setBody("Hello");

        exchange.getMessage().setBody(new ByteArrayInputStream("Test1".getBytes(StandardCharsets.UTF_8)));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertTrue(exchange.getMessage().hasHeaders());
        Assertions.assertTrue(exchange.getMessage().getHeaders().containsKey(AWS2S3Constants.BUCKET_NAME));
        assertEquals(
                "org.apache.camel.event.aws.s3.getObject",
                exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        assertEquals("camel.txt", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
        assertEquals("aws.s3.bucket.mycamel", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
    }

    @Test
    public void shouldLookupDataTypeTransformer() throws Exception {
        Transformer transformer = camelContext
                .getTransformerRegistry()
                .resolveTransformer(new TransformerKey("aws2-s3:application-cloudevents"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(AWS2S3CloudEventDataTypeTransformer.class, transformer.getClass());
    }
}
