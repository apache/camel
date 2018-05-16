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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link MongoDbEndpoint}.
 */
public class MongoDbComponent extends DefaultComponent {
    
    public static final Set<MongoDbOperation> WRITE_OPERATIONS = 
            new HashSet<>(Arrays.asList(MongoDbOperation.insert, MongoDbOperation.save, 
                    MongoDbOperation.update, MongoDbOperation.remove, MongoDbOperation.bulkWrite));
    
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbComponent.class);

    public MongoDbComponent() {
        this(null);
    }

    public MongoDbComponent(CamelContext context) {
        super(context);
    }
   
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        MongoDbEndpoint endpoint = new MongoDbEndpoint(uri, this);
        endpoint.setConnectionBean(remaining);
        setProperties(endpoint, parameters);
        
        return endpoint;
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
    }

    public static CamelMongoDbException wrapInCamelMongoDbException(Throwable t) {
        if (t instanceof CamelMongoDbException) {
            return (CamelMongoDbException) t;
        } else {
            return new CamelMongoDbException(t);
        }
    }

}
