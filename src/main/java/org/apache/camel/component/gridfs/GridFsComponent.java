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
package org.apache.camel.component.gridfs;

import com.mongodb.Mongo;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.CamelContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class GridFsComponent extends DefaultComponent {

    private final static Logger LOG = LoggerFactory.getLogger(GridFsComponent.class);

    private volatile Mongo db;

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (db == null) {
            db = CamelContextHelper.mandatoryLookup(getCamelContext(), remaining, Mongo.class);
            LOG.debug("Resolved the connection with the name {} as {}", remaining, db);
        }

        Endpoint endpoint = new GridFsEndpoint(uri, this);
        parameters.put("mongoConnection", db);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    @Override
    protected void doShutdown() throws Exception {
        if (db != null) {
            LOG.debug("Closing the connection {} on {}", db, this);
            db.close();
        }
        super.doShutdown();
    }

}
