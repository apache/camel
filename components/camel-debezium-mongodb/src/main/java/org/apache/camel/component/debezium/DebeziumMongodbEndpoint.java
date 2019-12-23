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

import io.debezium.data.Envelope;
import org.apache.camel.component.debezium.configuration.MongoDbConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.kafka.connect.data.Schema;

/**
 * Represents a Debezium MongoDB endpoint which is used to capture changes in MongoDB database so that that applications can see those changes and respond to them.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "debezium-mongodb", title = "Debezium MongoDB Connector", syntax = "debezium-mongodb:name", label = "database,nosql,mongodb", consumerOnly = true)
public final class DebeziumMongodbEndpoint extends DebeziumEndpoint<MongoDbConnectorEmbeddedDebeziumConfiguration> {

    @UriParam
    private MongoDbConnectorEmbeddedDebeziumConfiguration configuration;

    public DebeziumMongodbEndpoint(final String uri, final DebeziumMongodbComponent component, final MongoDbConnectorEmbeddedDebeziumConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public DebeziumMongodbEndpoint() {
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
    protected Object extractBodyValueFromValueStruct(Schema schema, Object value) {
        // according to DBZ docs, `after` field only presents on the create events, however for updates there is field `patch`
        // https://debezium.io/documentation/reference/0.10/connectors/mongodb.html
        // hence first we test for `after`, if null we return `patch` instead
        final Object after = extractFieldValueFromValueStruct(schema, value, Envelope.FieldName.AFTER);
        if (after != null) {
            return after;
        }
        return extractFieldValueFromValueStruct(schema, value, "patch");
    }
}
