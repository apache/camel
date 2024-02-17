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
package org.apache.camel.component.salesforce;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.ProducerEvent;
import com.sforce.eventbus.CamelEventMessage__e;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.salesforce.internal.client.PubSubApiClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Tag("standalone")
public class PubSubApiManualIT extends AbstractSalesforceTestBase {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    final Schema camelEventSchema = new Schema.Parser().parse(
            """
                    {
                        "type": "record",
                        "name": "CamelEventMessage__e",
                        "namespace": "com.sforce.eventbus",
                        "fields": [
                            {
                                "name": "CreatedDate",
                                "type": "long",
                                "doc": "CreatedDate:DateTime"
                            },
                            {
                                "name": "CreatedById",
                                "type": "string",
                                "doc": "CreatedBy:EntityId"
                            },
                            {
                                "name": "Message__c",
                                "type": [
                                    "null",
                                    "string"
                                ],
                                "doc": "Data:Text:00NDS00000mES97",
                                "default": null
                            }
                        ]
                    }
                    """);

    final Schema camelEvent2Schema = new Schema.Parser().parse(
            """
                    {
                        "type": "record",
                        "name": "CamelEventNote__e",
                        "namespace": "com.sforce.eventbus",
                        "fields": [
                            {
                                "name": "CreatedDate",
                                "type": "long",
                                "doc": "CreatedDate:DateTime"
                            },
                            {
                                "name": "CreatedById",
                                "type": "string",
                                "doc": "CreatedBy:EntityId"
                            },
                            {
                                "name": "Note__c",
                                "type": [
                                    "null",
                                    "string"
                                ],
                                "doc": "Data:Text:00NDS00000mZSIr",
                                "default": null
                            }
                        ]
                    }
                    """);

    private GenericRecord record() {
        return new GenericRecordBuilder(camelEventSchema)
                .set("Message__c", "hello world")
                .set("CreatedDate", System.currentTimeMillis() / 1000)
                .set("CreatedById", "123")
                .build();
    }

    @Test
    public void receiveEventsOverTime() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:timer1?period=1000&repeatCount=3")
                        .setBody(exchange -> List.of(record()))
                        .to("salesforce:pubSubPublish:/event/CamelEventMessage__e");
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:CamelTestTopic");
        mock.expectedMessageCount(3);
        mock.assertIsSatisfied(10000);
    }

    @Test
    public void canPublishAndReceivePojoEvents() throws InterruptedException {
        PojoEvent record = new PojoEvent();
        record.setMessage__c("hello world");
        record.setCreatedDate(System.currentTimeMillis() / 1000);
        record.setCreatedById("123");

        CamelEventMessage__e expectedRecord = new CamelEventMessage__e();
        expectedRecord.setMessageC(record.getMessage__c());
        expectedRecord.setCreatedDate(record.getCreatedDate());
        expectedRecord.setCreatedById(record.getCreatedById());

        MockEndpoint mock = getMockEndpoint("mock:CamelTestTopic");
        mock.expectedBodiesReceived(expectedRecord);

        template.requestBody("direct:publishCamelEventMessage", List.of(record));

        mock.assertIsSatisfied();
    }

    @Test
    public void canReceiveJsonEvent() throws InterruptedException {
        PojoEvent record = new PojoEvent();
        record.setMessage__c("hello world");
        record.setCreatedDate(System.currentTimeMillis() / 1000);
        record.setCreatedById("123");

        MockEndpoint mock = getMockEndpoint("mock:CamelTestTopicJson");
        mock.expectedMessageCount(1);

        template.requestBody("direct:publishCamelEventMessage", List.of(record));

        mock.assertIsSatisfied();
    }

    @Test
    public void canReceivePojoEvent() throws InterruptedException {
        PubSubPojoEvent record = new PubSubPojoEvent();
        record.setMessage__c("hello world");
        record.setCreatedDate(System.currentTimeMillis() / 1000);
        record.setCreatedById("123");

        MockEndpoint mock = getMockEndpoint("mock:CamelTestTopicPojo");
        mock.expectedBodiesReceived(record);

        template.requestBody("direct:publishCamelEventMessage", List.of(record));

        mock.assertIsSatisfied();
    }

    @Test
    public void canPublishJsonEvents() throws InterruptedException {
        String record = """
                {
                    "Message__c": "hello world",
                    "CreatedDate": 123,
                    "CreatedById": "123"
                }
                """;

        CamelEventMessage__e expectedRecord = new CamelEventMessage__e();
        expectedRecord.setMessageC("hello world");
        expectedRecord.setCreatedDate(123);
        expectedRecord.setCreatedById("123");

        MockEndpoint mock = getMockEndpoint("mock:CamelTestTopic");
        mock.expectedBodiesReceived(expectedRecord);

        template.requestBody("direct:publishCamelEventMessage", List.of(record));

        mock.assertIsSatisfied();
    }

    @Test
    public void canPublishProducerEvent() throws InterruptedException, IOException {
        CamelEventMessage__e record = new CamelEventMessage__e();
        record.setMessageC("hello world");
        record.setCreatedDate(123);
        record.setCreatedById("123");

        PubSubApiClient client = null;
        try {
            client = new PubSubApiClient(
                    component.getSession(), new SalesforceLoginConfig(),
                    "api.pubsub.salesforce.com", 7443, 0, 0);
            client.start();

            byte[] bytes;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().directBinaryEncoder(buffer, null);
            final SpecificDatumWriter<CamelEventMessage__e> writer = new SpecificDatumWriter<>(CamelEventMessage__e.class);
            writer.write(record, encoder);
            bytes = buffer.toByteArray();

            ProducerEvent.newBuilder()
                    .setSchemaId(client.getTopicInfo("/event/CamelEventMessage__e").getSchemaId())
                    .setPayload(ByteString.copyFrom(bytes))
                    .build();

            MockEndpoint mock = getMockEndpoint("mock:CamelTestTopic");
            mock.expectedBodiesReceived(record);

            template.requestBody("direct:publishCamelEventMessage", List.of(record));

            mock.assertIsSatisfied();
        } finally {
            if (client != null) {
                client.stop();
            }
        }
    }

    @Test
    public void canPublishAndReceiveGenericEvents() throws InterruptedException {
        CamelEventMessage__e expectedRecord = new CamelEventMessage__e();
        expectedRecord.setMessageC("hello world");
        expectedRecord.setCreatedDate(System.currentTimeMillis() / 1000);
        expectedRecord.setCreatedById("123");

        final GenericRecord record = new GenericRecordBuilder(camelEventSchema)
                .set("Message__c", "hello world")
                .set("CreatedDate", System.currentTimeMillis() / 1000)
                .set("CreatedById", "123")
                .build();

        final GenericRecord record2 = new GenericRecordBuilder(camelEvent2Schema)
                .set("Note__c", "hello world2")
                .set("CreatedDate", record.get("CreatedDate"))
                .set("CreatedById", "123")
                .build();

        MockEndpoint mock = getMockEndpoint("mock:CamelTestTopic");
        mock.expectedBodiesReceived(expectedRecord);

        MockEndpoint mock2 = getMockEndpoint("mock:CamelTestTopicEventNote");
        mock2.expectedBodiesReceived(record2);

        template.requestBody("direct:publishCamelEventMessage", List.of(record));
        template.requestBody("direct:publishCamelEventNote", List.of(record2));

        mock.assertIsSatisfied();
        mock2.assertIsSatisfied();
    }

    @Test
    public void canPublishAndReceiveSpecificEvents() throws InterruptedException {
        CamelEventMessage__e record = new CamelEventMessage__e();
        record.setMessageC("hello world");
        record.setCreatedDate(System.currentTimeMillis() / 1000);
        record.setCreatedById("123");

        MockEndpoint mock = getMockEndpoint("mock:CamelTestTopic");
        mock.expectedMessageCount(1);
        mock.whenAnyExchangeReceived(exchange -> {
            LOG.debug(exchange.getIn().getBody().toString());
            LOG.debug(exchange.getIn().getBody().getClass().getName());
        });

        template.requestBody("direct:publishCamelEventMessage", List.of(record));

        mock.assertIsSatisfied();
    }

    @Test
    public void canPublishAndReceiveABatchOfEvents() throws InterruptedException {
        final GenericRecord record = new GenericRecordBuilder(camelEventSchema)
                .set("Message__c", "hello world")
                .set("CreatedDate", System.currentTimeMillis() / 1000)
                .set("CreatedById", "123")
                .build();

        MockEndpoint mock = getMockEndpoint("mock:CamelTestTopic");
        mock.expectedMessageCount(3);

        template.requestBody("direct:publishCamelEventMessage", List.of(record, record, record));
        mock.assertIsSatisfied();
    }

    @Test
    public void canSubscribeWithReplayId() throws Exception {
        final GenericRecord record = new GenericRecordBuilder(camelEventSchema)
                .set("Message__c", "hello world")
                .set("CreatedDate", System.currentTimeMillis() / 1000)
                .set("CreatedById", "123")
                .build();

        MockEndpoint mock = getMockEndpoint("mock:CamelTestTopic");
        mock.expectedMessageCount(1);
        AtomicReference<String> replayId = new AtomicReference<>("");
        mock.whenAnyExchangeReceived(exchange -> {
            final String rId = exchange.getIn().getHeader("CamelSalesforcePubSubReplayId", String.class);
            replayId.set(rId);
        });

        // publish an initial event
        template.requestBody("direct:publishCamelEventMessage", List.of(record));
        mock.assertIsSatisfied();

        // store the replayId in properties
        LOG.debug("replayId: {}", replayId);
        context.getPropertiesComponent().addOverrideProperty("pubSubReplayId", replayId.get());

        // start a new route with the replayId
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("salesforce:pubSubSubscribe:/event/CamelEventNote__e" +
                     "?replayPreset=CUSTOM" +
                     "&pubSubReplayId={{pubSubReplayId}}")
                        .routeId("r.subscriberWithReplayId")
                        .autoStartup(false)
                        .to("mock:SubscriberWithReplayId");
            }
        });

        mock.reset();
        mock.expectedMessageCount(1);

        template.requestBody("direct:publishCamelEventMessage", List.of(record));
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:publishCamelEventMessage")
                        .to("salesforce:pubSubPublish:/event/CamelEventMessage__e");

                from("direct:publishCamelEventNote")
                        .to("salesforce:pubSubPublish:/event/CamelEventNote__e");

                from("salesforce:pubSubSubscribe:/event/CamelEventMessage__e?pubSubBatchSize=10")
                        .routeId("org.apache.camel.component.salesforce.sub1")
                        .log(LoggingLevel.DEBUG, "${body}")
                        .to("mock:CamelTestTopic");

                from("salesforce:pubSubSubscribe:/event/CamelEventMessage__e" +
                     "?pubSubBatchSize=10" +
                     "&pubSubDeserializeType=JSON")
                        .routeId("org.apache.camel.component.salesforce.sub3")
                        .log(LoggingLevel.DEBUG, "${body}")
                        .to("mock:CamelTestTopicJson");

                from("salesforce:pubSubSubscribe:/event/CamelEventMessage__e" +
                     "?pubSubBatchSize=10" +
                     "&pubSubDeserializeType=POJO" +
                     "&pubSubPojoClass=org.apache.camel.component.salesforce.PubSubPojoEvent")
                        .routeId("org.apache.camel.component.salesforce.sub4")
                        .log(LoggingLevel.DEBUG, "${body}")
                        .to("mock:CamelTestTopicPojo");

                from("salesforce:pubSubSubscribe:/event/CamelEventNote__e?pubSubBatchSize=10")
                        .routeId("org.apache.camel.component.salesforce.sub2")
                        .log(LoggingLevel.DEBUG, "${body}")
                        .to("mock:CamelTestTopicEventNote");
            }
        };
    }

    public static class PojoEvent {
        private String Message__c;
        private long CreatedDate;
        private String CreatedById;

        @Override
        public String toString() {
            return "PojoEvent{" +
                   "Message__c='" + Message__c + '\'' +
                   ", CreatedDate=" + CreatedDate +
                   ", CreatedById='" + CreatedById + '\'' +
                   '}';
        }

        public String getMessage__c() {
            return Message__c;
        }

        public void setMessage__c(String message__c) {
            this.Message__c = message__c;
        }

        public long getCreatedDate() {
            return CreatedDate;
        }

        public void setCreatedDate(long createdDate) {
            this.CreatedDate = createdDate;
        }

        public String getCreatedById() {
            return CreatedById;
        }

        public void setCreatedById(String createdById) {
            this.CreatedById = createdById;
        }
    }
}
