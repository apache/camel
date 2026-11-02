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

import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.debezium.data.Envelope;
import io.debezium.relational.history.HistoryRecord;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.debezium.configuration.EmbeddedDebeziumConfiguration;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.header.Header;
import org.apache.kafka.connect.source.SourceRecord;

/**
 * Base class for debezium endpoint implementation
 */
public abstract class DebeziumEndpoint<C extends EmbeddedDebeziumConfiguration> extends DefaultEndpoint {

    private static final String DEBEZIUM_SCHEMA_NAME_PREFIX = "io.debezium.";
    // the name of the field or header that the ExtractNewRecordState SMT adds for the operation with its default prefix
    private static final String FLATTENED_OPERATION = "__op";

    protected DebeziumEndpoint(String uri, DebeziumComponent<C> component) {
        super(uri, component);
    }

    protected DebeziumEndpoint() {
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException(
                "Cannot produce from a DebeziumEndpoint: "
                                                + getEndpointUri());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        DebeziumConsumer consumer = new DebeziumConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public ExecutorService createExecutor(Object source) {
        return getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(source,
                "DebeziumConsumer");
    }

    public Exchange createDbzExchange(DebeziumConsumer consumer, final SourceRecord sourceRecord) {
        final Exchange exchange;
        if (consumer != null) {
            exchange = consumer.createExchange(false);
        } else {
            exchange = super.createExchange();
        }

        final Message message = exchange.getIn();

        final Schema valueSchema = sourceRecord.valueSchema();
        final Object value = sourceRecord.value();

        // extract values from SourceRecord
        final Map<String, Object> sourceMetadata = extractSourceMetadataValueFromValueStruct(valueSchema, value);
        final Object operation = extractOperation(sourceRecord, valueSchema, value);
        final Object before = extractValueFromValueStruct(valueSchema, value, Envelope.FieldName.BEFORE);
        final Object body = extractBodyValueFromValueStruct(valueSchema, value);
        final Object timestamp = extractValueFromValueStruct(valueSchema, value, Envelope.FieldName.TIMESTAMP);
        final Object ddl = extractValueFromValueStruct(valueSchema, value, HistoryRecord.Fields.DDL_STATEMENTS);
        // set message headers
        message.setHeader(DebeziumConstants.HEADER_IDENTIFIER, sourceRecord.topic());
        message.setHeader(DebeziumConstants.HEADER_KEY, sourceRecord.key());
        message.setHeader(DebeziumConstants.HEADER_SOURCE_METADATA, sourceMetadata);
        message.setHeader(DebeziumConstants.HEADER_OPERATION, operation);
        message.setHeader(DebeziumConstants.HEADER_BEFORE, before);
        message.setHeader(DebeziumConstants.HEADER_TIMESTAMP, timestamp);
        message.setHeader(DebeziumConstants.HEADER_DDL_SQL, ddl);
        message.setHeader(Exchange.MESSAGE_TIMESTAMP, timestamp);

        message.setBody(body);

        return exchange;
    }

    public abstract C getConfiguration();

    public abstract void setConfiguration(C configuration);

    protected Object extractBodyValueFromValueStruct(final Schema schema, final Object value) {
        // by default, we will extract the value from field `after`, however if other connector needs different field, this method needs to be overriden
        return extractFieldValueFromValueStruct(schema, value, Envelope.FieldName.AFTER);
    }

    protected Object extractFieldValueFromValueStruct(final Schema schema, final Object value, final String fieldName) {
        // first we try with normal extraction from value struct
        final Object valueExtracted = extractValueFromValueStruct(schema, value, fieldName);

        // we could have anything other than struct, or the flattened row of the ExtractNewRecordState SMT, we just return that
        if (valueExtracted == null && (!isSchemaAStructSchema(schema) || isFlattenedRecordValue(schema, value))) {
            return value;
        }
        return valueExtracted;
    }

    /**
     * Whether the value is a row that a single message transformation such as ExtractNewRecordState (or
     * ExtractNewDocumentState for MongoDB) flattened out of the change event envelope: a Struct without an
     * {@code after} field, that is not one of the Debezium events such as a schema change, a heartbeat or a transaction
     * metadata event (their schema names start with {@code io.debezium.}).
     */
    private boolean isFlattenedRecordValue(final Schema schema, final Object value) {
        return value != null && isSchemaAStructSchema(schema)
                && schema.field(Envelope.FieldName.AFTER) == null
                && (schema.name() == null || !schema.name().startsWith(DEBEZIUM_SCHEMA_NAME_PREFIX));
    }

    private Object extractOperation(final SourceRecord sourceRecord, final Schema schema, final Object value) {
        final Object operation = extractValueFromValueStruct(schema, value, Envelope.FieldName.OPERATION);
        if (operation != null || !isFlattenedRecordValue(schema, value)) {
            return operation;
        }
        // the ExtractNewRecordState SMT adds the operation as the __op field (add.fields=op) or header (add.headers=op)
        final Object flattenedOperation = extractValueFromValueStruct(schema, value, FLATTENED_OPERATION);
        if (flattenedOperation != null) {
            return flattenedOperation;
        }
        final Header header = sourceRecord.headers().lastWithName(FLATTENED_OPERATION);
        return header != null ? header.value() : null;
    }

    private Map<String, Object> extractSourceMetadataValueFromValueStruct(final Schema schema, final Object value) {
        // we want to convert metadata to map since it facilitate usage and also struct structure is not needed for the metadata
        final Object valueExtracted = extractValueFromValueStruct(schema, value, Envelope.FieldName.SOURCE);

        if (valueExtracted != null) {
            return DebeziumTypeConverter.toMap((Struct) valueExtracted);
        }
        return null;
    }

    protected Object extractValueFromValueStruct(final Schema schema, final Object value, final String fieldName) {
        // first we check if we have a value and a schema of struct type
        if (isSchemaAStructSchema(schema) && value != null) {
            // now we return our desired fieldName
            try {
                final Struct valueStruct = (Struct) value;
                return valueStruct.get(fieldName);
            } catch (DataException e) {
                // we return null instead since this exception thrown when no value set or field doesn't exist
                return null;
            }
        }
        return null;
    }

    private boolean isSchemaAStructSchema(final Schema schema) {
        return schema != null && schema.type().equals(Schema.Type.STRUCT);
    }
}
