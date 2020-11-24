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
package org.apache.camel.test.infra.consul.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsulServiceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(ConsulServiceFactory.class);

    private ConsulServiceFactory() {

    }

    public static ConsulService createService() {
        String instanceType = System.getProperty("consul.instance.type");

        if (instanceType == null || instanceType.equals("local-consul-container")) {
            return new ConsulLocalContainerService();
        }

        if (instanceType.equals("remote")) {
            return new ConsulRemoteService();
        }

        LOG.error("Consul instance must be one of 'local-consul-container' or 'remote");
        throw new UnsupportedOperationException("Invalid Consul instance type");
    }
}
