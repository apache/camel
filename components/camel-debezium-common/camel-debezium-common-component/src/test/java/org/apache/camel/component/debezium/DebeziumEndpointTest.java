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
package org.apache.camel.component.debezium;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import io.debezium.data.Envelope;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.debezium.configuration.FileConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(MockitoJUnitRunner.class)
public class DebeziumEndpointTest {

    private DebeziumEndpoint debeziumEndpoint;

    @Mock
    private Processor processor;

    @Before
    public void setUp() {
        debeziumEndpoint = new DebeziumTestEndpoint("", new DebeziumTestComponent(new DefaultCamelContext()),
                new FileConnectorEmbeddedDebeziumConfiguration());
    }

    @Test
    public void testIfCreatesConsumer() throws Exception {
        final Consumer debeziumConsumer = debeziumEndpoint.createConsumer(processor);

        assertNotNull(debeziumConsumer);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testIfFailsToCreateProducer() throws Exception {
        final Producer debeziumConsumer = debeziumEndpoint.createProducer();
    }

    @Test
    public void testIfCreatesExchangeFromSourceCreateRecord() {
        final SourceRecord sourceRecord = createCreateRecord();

        final Exchange exchange = debeziumEndpoint.createDbzExchange(sourceRecord);
        final Message inMessage = exchange.getIn();

        assertNotNull(exchange);
        // assert headers
        assertEquals("dummy", inMessage.getHeader(DebeziumConstants.HEADER_IDENTIFIER));
        assertEquals(Envelope.Operation.CREATE.code(),
                inMessage.getHeader(DebeziumConstants.HEADER_OPERATION));
        final Struct key = (Struct)inMessage.getHeader(DebeziumConstants.HEADER_KEY);
        assertEquals(12345, key.getInt32("id").intValue());
        assertSourceMetadata(inMessage);
        assertNotNull(inMessage.getHeader(DebeziumConstants.HEADER_TIMESTAMP));

        // assert value
        final Struct body = (Struct)inMessage.getBody();
        assertNotNull(body);
        assertEquals((byte)1, body.getInt8("id").byteValue());

        // assert schema
        assertSchema(body.schema());
    }

    @Test
    public void testIfCreatesExchangeFromSourceDeleteRecord() {
        final SourceRecord sourceRecord = createDeleteRecord();

        final Exchange exchange = debeziumEndpoint.createDbzExchange(sourceRecord);
        final Message inMessage = exchange.getIn();

        assertNotNull(exchange);
        // assert headers
        assertEquals("dummy", inMessage.getHeader(DebeziumConstants.HEADER_IDENTIFIER));
        assertEquals(Envelope.Operation.DELETE.code(),
                inMessage.getHeader(DebeziumConstants.HEADER_OPERATION));
        final Struct key = (Struct)inMessage.getHeader(DebeziumConstants.HEADER_KEY);
        assertEquals(12345, key.getInt32("id").intValue());
        assertNotNull(inMessage.getHeader(DebeziumConstants.HEADER_BEFORE));

        // assert value
        final Struct body = (Struct)inMessage.getBody();
        assertNull(body); // we expect body to be null since is a delete
    }

    @Test
    public void testIfCreatesExchangeFromSourceDeleteRecordWithNull() {
        final SourceRecord sourceRecord = createDeleteRecordWithNull();

        final Exchange exchange = debeziumEndpoint.createDbzExchange(sourceRecord);
        final Message inMessage = exchange.getIn();

        assertNotNull(exchange);
        // assert headers
        assertEquals("dummy", inMessage.getHeader(DebeziumConstants.HEADER_IDENTIFIER));
        final Struct key = (Struct)inMessage.getHeader(DebeziumConstants.HEADER_KEY);
        assertEquals(12345, key.getInt32("id").intValue());

        // assert value
        final Struct body = (Struct)inMessage.getBody();
        assertNull(body);
    }

    @Test
    public void testIfCreatesExchangeFromSourceUpdateRecord() {
        final SourceRecord sourceRecord = createUpdateRecord();

        final Exchange exchange = debeziumEndpoint.createDbzExchange(sourceRecord);
        final Message inMessage = exchange.getIn();

        assertNotNull(exchange);
        // assert headers
        assertEquals("dummy", inMessage.getHeader(DebeziumConstants.HEADER_IDENTIFIER));
        assertEquals(Envelope.Operation.UPDATE.code(),
                inMessage.getHeader(DebeziumConstants.HEADER_OPERATION));
        final Struct key = (Struct)inMessage.getHeader(DebeziumConstants.HEADER_KEY);
        assertEquals(12345, key.getInt32("id").intValue());
        assertSourceMetadata(inMessage);

        // assert value
        final Struct before = (Struct) inMessage.getHeader(DebeziumConstants.HEADER_BEFORE);
        final Struct after = (Struct)inMessage.getBody();
        assertNotNull(before);
        assertNotNull(after);
        assertEquals((byte)1, before.getInt8("id").byteValue());
        assertEquals((byte)2, after.getInt8("id").byteValue());
    }

    @Test
    public void testIfCreatesExchangeFromSourceRecordOtherThanStruct() {
        final SourceRecord sourceRecord = createStringRecord();

        final Exchange exchange = debeziumEndpoint.createDbzExchange(sourceRecord);
        final Message inMessage = exchange.getIn();

        assertNotNull(exchange);

        // assert headers
        assertEquals("dummy", inMessage.getHeader(DebeziumConstants.HEADER_IDENTIFIER));
        assertNull(inMessage.getHeader(DebeziumConstants.HEADER_OPERATION));

        // assert value
        final String value = (String) inMessage.getBody();
        assertEquals(sourceRecord.value(), value);
    }

    @Test
    public void testIfHandlesUnknownSchema() {
        final SourceRecord sourceRecord = createUnknownUnnamedSchemaRecord();

        final Exchange exchange = debeziumEndpoint.createDbzExchange(sourceRecord);
        final Message inMessage = exchange.getIn();

        assertNotNull(exchange);
        // assert headers
        assertEquals("dummy", inMessage.getHeader(DebeziumConstants.HEADER_IDENTIFIER));
        assertNull(inMessage.getHeader(DebeziumConstants.HEADER_OPERATION));
        assertNull(inMessage.getHeader(DebeziumConstants.HEADER_KEY));

        // assert value
        final Struct body = (Struct)inMessage.getBody();
        // we have to get value of after with struct, we are strict about this case
        assertNull(body);
    }

    private SourceRecord createCreateRecord() {
        final Schema recordSchema = SchemaBuilder.struct().field("id", SchemaBuilder.int8()).build();
        final Schema sourceSchema = SchemaBuilder.struct().field("lsn", SchemaBuilder.int32()).build();
        Envelope envelope = Envelope.defineSchema().withName("dummy.Envelope").withRecord(recordSchema)
                .withSource(sourceSchema).build();
        final Struct after = new Struct(recordSchema);
        final Struct source = new Struct(sourceSchema);

        after.put("id", (byte)1);
        source.put("lsn", 1234);
        final Struct payload = envelope.create(after, source, Instant.now());
        return new SourceRecord(new HashMap<>(), createSourceOffset(), "dummy", createKeySchema(),
                createKeyRecord(), envelope.schema(), payload);
    }

    private SourceRecord createDeleteRecord() {
        final Schema recordSchema = SchemaBuilder.struct().field("id", SchemaBuilder.int8()).build();
        Envelope envelope = Envelope.defineSchema().withName("dummy.Envelope").withRecord(recordSchema)
                .withSource(SchemaBuilder.struct().build()).build();
        final Struct before = new Struct(recordSchema);
        before.put("id", (byte)1);
        final Struct payload = envelope.delete(before, null, Instant.now());
        return new SourceRecord(new HashMap<>(), createSourceOffset(), "dummy", createKeySchema(),
                createKeyRecord(), envelope.schema(), payload);
    }

    private SourceRecord createDeleteRecordWithNull() {
        final Schema recordSchema = SchemaBuilder.struct().field("id", SchemaBuilder.int8()).build();
        Envelope envelope = Envelope.defineSchema().withName("dummy.Envelope").withRecord(recordSchema)
                .withSource(SchemaBuilder.struct().build()).build();
        final Struct before = new Struct(recordSchema);
        before.put("id", (byte)1);
        return new SourceRecord(new HashMap<>(), createSourceOffset(), "dummy", createKeySchema(),
                createKeyRecord(), null, null);
    }

    private SourceRecord createUpdateRecord() {
        final Schema recordSchema = SchemaBuilder.struct().field("id", SchemaBuilder.int8()).build();
        final Schema sourceSchema = SchemaBuilder.struct().field("lsn", SchemaBuilder.int32()).build();
        Envelope envelope = Envelope.defineSchema().withName("dummy.Envelope").withRecord(recordSchema)
                .withSource(sourceSchema).build();
        final Struct before = new Struct(recordSchema);
        final Struct source = new Struct(sourceSchema);
        final Struct after = new Struct(recordSchema);

        before.put("id", (byte)1);
        after.put("id", (byte)2);
        source.put("lsn", 1234);
        final Struct payload = envelope.update(before, after, source, Instant.now());
        return new SourceRecord(new HashMap<>(), createSourceOffset(), "dummy", createKeySchema(),
                createKeyRecord(), envelope.schema(), payload);
    }

    private SourceRecord createUnknownUnnamedSchemaRecord() {
        final Schema recordSchema = SchemaBuilder.struct().field("id", SchemaBuilder.int8()).build();
        final Struct before = new Struct(recordSchema);
        before.put("id", (byte)1);
        return new SourceRecord(new HashMap<>(), new HashMap<>(), "dummy", recordSchema, before);
    }

    private SourceRecord createStringRecord() {
        final Schema recordSchema = Schema.STRING_SCHEMA;
        return new SourceRecord(new HashMap<>(), createSourceOffset(), "dummy", recordSchema, "test_record");
    }

    private HashMap<String, ?> createSourceOffset() {
        final HashMap<String, Integer> sourceOffsets = new HashMap<>();
        sourceOffsets.put("pos", 111);

        return sourceOffsets;
    }

    private Schema createKeySchema() {
        return SchemaBuilder.struct().field("id", SchemaBuilder.int32().build());
    }

    private Struct createKeyRecord() {
        final Struct key = new Struct(createKeySchema());
        key.put("id", 12345);

        return key;
    }

    private void assertSourceMetadata(final Message inMessage) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> source = inMessage.getHeader(DebeziumConstants.HEADER_SOURCE_METADATA, Map.class);
        assertEquals(1234, source.get("lsn"));
    }

    private void assertSchema(final Schema schema) {
        assertNotNull(schema);
        assertFalse(schema.fields().isEmpty());
    }
}
