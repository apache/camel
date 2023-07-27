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
import org.apache.kafka.connect.source.SourceRecord;

/**
 * Base class for debezium endpoint implementation
 */
public abstract class DebeziumEndpoint<C extends EmbeddedDebeziumConfiguration> extends DefaultEndpoint {

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

    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this,
                "DebeziumConsumer");
    }

    public Exchange createDbzExchange(DebeziumConsumer consumer, final SourceRecord record) {
        final Exchange exchange;
        if (consumer != null) {
            exchange = consumer.createExchange(false);
        } else {
            exchange = super.createExchange();
        }

        final Message message = exchange.getIn();

        final Schema valueSchema = record.valueSchema();
        final Object value = record.value();

        // extract values from SourceRecord
        final Map<String, Object> sourceMetadata = extractSourceMetadataValueFromValueStruct(valueSchema, value);
        final Object operation = extractValueFromValueStruct(valueSchema, value, Envelope.FieldName.OPERATION);
        final Object before = extractValueFromValueStruct(valueSchema, value, Envelope.FieldName.BEFORE);
        final Object body = extractBodyValueFromValueStruct(valueSchema, value);
        final Object timestamp = extractValueFromValueStruct(valueSchema, value, Envelope.FieldName.TIMESTAMP);
        final Object ddl = extractValueFromValueStruct(valueSchema, value, HistoryRecord.Fields.DDL_STATEMENTS);
        // set message headers
        message.setHeader(DebeziumConstants.HEADER_IDENTIFIER, record.topic());
        message.setHeader(DebeziumConstants.HEADER_KEY, record.key());
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

        if (valueExtracted == null && !isSchemaAStructSchema(schema)) { // we could have anything other than struct, we just return that
            return value;
        }
        return valueExtracted;
    }

    private Map<String, Object> extractSourceMetadataValueFromValueStruct(final Schema schema, final Object value) {
        // we want to convert metadata to map since it facilitate usage and also struct structure is not needed for the metadata
        final Object valueExtracted = extractValueFromValueStruct(schema, value, Envelope.FieldName.SOURCE);

        if (valueExtracted != null) {
            return DebeziumTypeConverter.toMap((Struct) valueExtracted);
        }
        return null;
    }

    private Object extractValueFromValueStruct(final Schema schema, final Object value, final String fieldName) {
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
