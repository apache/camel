/**
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

import java.util.Map;

import com.mongodb.Mongo;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.CamelContextHelper;

/**
 * Represents the component that manages {@link MongoDbEndpoint}.
 */
public class MongoDbComponent extends DefaultComponent {

    /**
     * Should access a singleton of type Mongo
     */
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Mongo db = CamelContextHelper.mandatoryLookup(getCamelContext(), remaining, Mongo.class);

        Endpoint endpoint = new MongoDbEndpoint(uri, this);
        parameters.put("mongoConnection", db);
        setProperties(endpoint, parameters);
        
        return endpoint;
    }

    public static CamelMongoDbException wrapInCamelMongoDbException(Throwable t) {
        if (t instanceof CamelMongoDbException) {
            return (CamelMongoDbException) t;
        } else {
            return new CamelMongoDbException(t);
        }
    }
    
    
}
