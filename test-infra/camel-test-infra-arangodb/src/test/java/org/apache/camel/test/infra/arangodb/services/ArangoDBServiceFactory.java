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
package org.apache.camel.test.infra.arangodb.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArangoDBServiceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(ArangoDBServiceFactory.class);

    private ArangoDBServiceFactory() {

    }

    public static ArangoDBService createService() {
        String instanceType = System.getProperty("arangodb.instance.type");

        if (instanceType == null || instanceType.equals("local-arangodb-container")) {
            return new ArangoDBLocalContainerService();
        }

        if (instanceType.equals("remote")) {
            return new ArangoDBRemoteService();
        }

        LOG.error("ArangoDB instance must be one of 'local-arangodb-container' or 'remote");
        throw new UnsupportedOperationException("Invalid ArangoDB instance type");
    }
}
