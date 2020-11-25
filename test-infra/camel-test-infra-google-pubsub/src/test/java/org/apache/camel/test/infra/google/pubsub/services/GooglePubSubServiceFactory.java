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
package org.apache.camel.test.infra.google.pubsub.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GooglePubSubServiceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(GooglePubSubServiceFactory.class);

    private GooglePubSubServiceFactory() {

    }

    public static GooglePubSubService createService() {
        String instanceType = System.getProperty("googlepubsub.instance.type");

        if (instanceType == null || instanceType.equals("local-googlepubsub-container")) {
            return new GooglePubSubLocalContainerService();
        }

        if (instanceType.equals("remote")) {
            return new GooglePubSubRemoteService();
        }

        LOG.error("GooglePubSub instance must be one of 'local-googlepubsub-container' or 'remote");
        throw new UnsupportedOperationException("Invalid GooglePubSub instance type");
    }
}
