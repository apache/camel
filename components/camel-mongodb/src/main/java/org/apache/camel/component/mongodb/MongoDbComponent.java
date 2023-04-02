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
package org.apache.camel.component.mongodb;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.mongodb.client.MongoClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.mongodb.meta.MongoDBMetaExtension;
import org.apache.camel.component.mongodb.verifier.MongoComponentVerifierExtension;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("mongodb")
public class MongoDbComponent extends DefaultComponent {

    public static final Set<MongoDbOperation> WRITE_OPERATIONS = EnumSet.copyOf(
            Arrays.asList(
                    MongoDbOperation.insert,
                    MongoDbOperation.save,
                    MongoDbOperation.update,
                    MongoDbOperation.remove));

    @Metadata(autowired = true)
    private MongoClient mongoConnection;

    public MongoDbComponent() {
        this(null);
    }

    public MongoDbComponent(CamelContext context) {
        super(context);
        registerExtension(MongoComponentVerifierExtension::new);
        registerExtension(MongoDBMetaExtension::new);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MongoDbEndpoint endpoint = new MongoDbEndpoint(uri, this);
        endpoint.setConnectionBean(remaining);
        endpoint.setMongoConnection(mongoConnection);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    /**
     * Get the connection bean client used for connection.
     */
    public MongoClient getMongoConnection() {
        return mongoConnection;
    }

    /**
     * Shared client used for connection. All endpoints generated from the component will share this connection client.
     */
    public void setMongoConnection(MongoClient mongoConnection) {
        this.mongoConnection = mongoConnection;
    }

    public static CamelMongoDbException wrapInCamelMongoDbException(Throwable t) {
        if (t instanceof CamelMongoDbException) {
            return (CamelMongoDbException) t;
        } else {
            return new CamelMongoDbException(t);
        }
    }
}
