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

import java.util.Map;

import io.debezium.connector.mongodb.MongoDbFieldName;
import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.component.debezium.DebeziumConstants;
import org.apache.camel.component.debezium.DebeziumConsumer;
import org.apache.camel.component.debezium.DebeziumEndpoint;
import org.apache.camel.component.debezium.DebeziumTypeConverter;
import org.apache.camel.component.debezium.mongodb.configuration.MongoDbConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;

/**
 * Capture changes from a MongoDB database.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "debezium-mongodb", title = "Debezium MongoDB Connector",
             syntax = "debezium-mongodb:name", category = { Category.DATABASE }, consumerOnly = true,
             headersClass = DebeziumConstants.class)
@BodyTypes(consumerOut = @BodyType(value = { Carrier.TEXT, Carrier.NULL }, javaType = "java.lang.String", format = "json",
                                   dependsOn = "additionalProperties",
                                   description = "The document after the change as a JSON String (MongoDB extended JSON):"
                                                 + " unmarshal it to work with its fields. No body for a delete or a tombstone,"
                                                 + " nor for an update when captureMode does not look up the full document or the"
                                                 + " document is gone by then;"
                                                 + " the operation and the key are in headers, and the changed fields of an"
                                                 + " update in the CamelDebeziumUpdateDescription header"))
public final class DebeziumMongodbEndpoint extends DebeziumEndpoint<MongoDbConnectorEmbeddedDebeziumConfiguration>
        implements EndpointServiceLocation {

    @UriParam
    private MongoDbConnectorEmbeddedDebeziumConfiguration configuration;

    public DebeziumMongodbEndpoint(final String uri, final DebeziumMongodbComponent component,
                                   final MongoDbConnectorEmbeddedDebeziumConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public DebeziumMongodbEndpoint() {
    }

    @Override
    public String getServiceUrl() {
        return configuration.getMongodbConnectionString();
    }

    @Override
    public String getServiceProtocol() {
        return "mongodb";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getMongodbUser() != null) {
            return Map.of("username", configuration.getMongodbUser());
        }
        return null;
    }

    @Override
    public MongoDbConnectorEmbeddedDebeziumConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(final MongoDbConnectorEmbeddedDebeziumConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Exchange createDbzExchange(final DebeziumConsumer consumer, final SourceRecord sourceRecord) {
        final Exchange exchange = super.createDbzExchange(consumer, sourceRecord);
        // an update has the changed fields in updateDescription, and no `after` value unless captureMode looks up the
        // full document
        final Object updateDescription = extractValueFromValueStruct(sourceRecord.valueSchema(), sourceRecord.value(),
                MongoDbFieldName.UPDATE_DESCRIPTION);
        if (updateDescription instanceof Struct struct) {
            exchange.getIn().setHeader(DebeziumConstants.HEADER_UPDATE_DESCRIPTION, DebeziumTypeConverter.toMap(struct));
        }
        return exchange;
    }
}
