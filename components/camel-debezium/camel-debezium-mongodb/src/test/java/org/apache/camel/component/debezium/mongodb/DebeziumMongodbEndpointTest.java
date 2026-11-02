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
package org.apache.camel.component.debezium.mongodb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.debezium.connector.mongodb.MongoDbFieldName;
import io.debezium.connector.mongodb.MongoDbSchemaFactory;
import io.debezium.data.Envelope;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.debezium.DebeziumConstants;
import org.apache.camel.component.debezium.mongodb.configuration.MongoDbConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DebeziumMongodbEndpointTest {

    private static final Schema ENVELOPE_SCHEMA = SchemaBuilder.struct().name("dummy.inventory.customers.Envelope")
            .field(Envelope.FieldName.AFTER, Schema.OPTIONAL_STRING_SCHEMA)
            .field(MongoDbFieldName.UPDATE_DESCRIPTION, MongoDbSchemaFactory.get().updatedDescriptionSchema())
            .field(Envelope.FieldName.OPERATION, Schema.STRING_SCHEMA)
            .field(Envelope.FieldName.TIMESTAMP, Schema.OPTIONAL_INT64_SCHEMA)
            .build();

    private DebeziumMongodbEndpoint endpoint;

    @BeforeEach
    public void setUp() {
        endpoint = new DebeziumMongodbEndpoint(
                "debezium-mongodb:dummy", new DebeziumMongodbComponent(new DefaultCamelContext()),
                new MongoDbConnectorEmbeddedDebeziumConfiguration());
    }

    @Test
    void testIfCreatesExchangeFromCreateRecord() {
        final Struct value = new Struct(ENVELOPE_SCHEMA)
                .put(Envelope.FieldName.AFTER, "{\"_id\": 1, \"name\": \"jane\"}")
                .put(Envelope.FieldName.OPERATION, Envelope.Operation.CREATE.code())
                .put(Envelope.FieldName.TIMESTAMP, 1234L);

        final Message inMessage = endpoint.createDbzExchange(null, createRecord(ENVELOPE_SCHEMA, value)).getIn();

        assertEquals(Envelope.Operation.CREATE.code(), inMessage.getHeader(DebeziumConstants.HEADER_OPERATION));
        assertEquals("{\"_id\": 1, \"name\": \"jane\"}", inMessage.getBody());
        assertNull(inMessage.getHeader(DebeziumConstants.HEADER_UPDATE_DESCRIPTION));
    }

    @Test
    void testIfCreatesExchangeFromUpdateRecordWithoutFullDocument() {
        final Schema updateDescriptionSchema = ENVELOPE_SCHEMA.field(MongoDbFieldName.UPDATE_DESCRIPTION).schema();
        final Schema truncatedArraySchema = updateDescriptionSchema.field(MongoDbFieldName.TRUNCATED_ARRAYS).schema()
                .valueSchema();
        final Struct updateDescription = new Struct(updateDescriptionSchema)
                .put(MongoDbFieldName.UPDATED_FIELDS, "{\"name\": \"john\"}")
                .put(MongoDbFieldName.REMOVED_FIELDS, List.of("nickname"))
                .put(MongoDbFieldName.TRUNCATED_ARRAYS, List.of(new Struct(truncatedArraySchema)
                        .put(MongoDbFieldName.ARRAY_FIELD_NAME, "tags")
                        .put(MongoDbFieldName.ARRAY_NEW_SIZE, 2)));
        // captureMode=change_streams does not look up the full document, so an update has no `after`
        final Struct value = new Struct(ENVELOPE_SCHEMA)
                .put(MongoDbFieldName.UPDATE_DESCRIPTION, updateDescription)
                .put(Envelope.FieldName.OPERATION, Envelope.Operation.UPDATE.code())
                .put(Envelope.FieldName.TIMESTAMP, 1234L);

        final Message inMessage = endpoint.createDbzExchange(null, createRecord(ENVELOPE_SCHEMA, value)).getIn();

        assertEquals(Envelope.Operation.UPDATE.code(), inMessage.getHeader(DebeziumConstants.HEADER_OPERATION));
        assertNull(inMessage.getBody());

        final Map<?, ?> header = inMessage.getHeader(DebeziumConstants.HEADER_UPDATE_DESCRIPTION, Map.class);
        assertNotNull(header);
        assertEquals("{\"name\": \"john\"}", header.get(MongoDbFieldName.UPDATED_FIELDS));
        assertEquals(List.of("nickname"), header.get(MongoDbFieldName.REMOVED_FIELDS));
        assertEquals(List.of(Map.of(MongoDbFieldName.ARRAY_FIELD_NAME, "tags", MongoDbFieldName.ARRAY_NEW_SIZE, 2)),
                header.get(MongoDbFieldName.TRUNCATED_ARRAYS));
    }

    @Test
    void testIfCreatesExchangeFromFlattenedRecord() {
        // the document flattened by the ExtractNewDocumentState SMT
        final Schema documentSchema = SchemaBuilder.struct().name("dummy.inventory.customers")
                .field("_id", Schema.INT32_SCHEMA)
                .field("name", Schema.STRING_SCHEMA)
                .build();
        final Struct document = new Struct(documentSchema).put("_id", 1).put("name", "jane");

        final Exchange exchange = endpoint.createDbzExchange(null, createRecord(documentSchema, document));
        final Message inMessage = exchange.getIn();

        final Struct body = inMessage.getBody(Struct.class);
        assertNotNull(body);
        assertEquals("jane", body.getString("name"));
        assertNull(inMessage.getHeader(DebeziumConstants.HEADER_UPDATE_DESCRIPTION));
    }

    private static SourceRecord createRecord(final Schema valueSchema, final Struct value) {
        return new SourceRecord(new HashMap<>(), new HashMap<>(), "dummy", null, null, valueSchema, value);
    }
}
