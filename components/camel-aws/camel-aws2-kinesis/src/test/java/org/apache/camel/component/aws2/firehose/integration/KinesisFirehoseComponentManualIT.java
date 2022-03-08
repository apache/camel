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
package org.apache.camel.component.aws2.firehose.integration;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.firehose.KinesisFirehose2Constants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.Record;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("Must be manually tested.")
public class KinesisFirehoseComponentManualIT extends CamelTestSupport {

    @BindToRegistry("FirehoseClient")
    FirehoseClient client = FirehoseClient.builder().build();

    @Test
    public void testFirehoseRouting() {
        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("my message text");
            }
        });
        assertNotNull(exchange.getIn().getHeader(KinesisFirehose2Constants.RECORD_ID));
    }

    @Test
    public void testFirehoseBatchRouting() {
        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) {
                List<Record> recs = new ArrayList<Record>();
                Record rec = Record.builder().data(SdkBytes.fromString("Test1", Charset.defaultCharset())).build();
                Record rec1 = Record.builder().data(SdkBytes.fromString("Test2", Charset.defaultCharset())).build();
                recs.add(rec);
                recs.add(rec1);
                exchange.getIn().setBody(recs);
            }
        });
        assertNotNull(exchange.getIn().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("aws2-kinesis-firehose://cc?amazonKinesisFirehoseClient=#FirehoseClient&operation=sendBatchRecord");
            }
        };
    }
}
