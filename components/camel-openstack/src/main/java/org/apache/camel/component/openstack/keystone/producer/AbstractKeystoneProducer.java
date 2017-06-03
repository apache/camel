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
package org.apache.camel.component.openstack.keystone.producer;

import org.apache.camel.component.openstack.common.AbstractOpenstackEndpoint;
import org.apache.camel.component.openstack.common.AbstractOpenstackProducer;
import org.openstack4j.api.OSClient;


public abstract class AbstractKeystoneProducer extends AbstractOpenstackProducer {

    OSClient.OSClientV3 osV3Client;

    public AbstractKeystoneProducer(AbstractOpenstackEndpoint endpoint, OSClient client) {
        super(endpoint, client);
        if (!(client instanceof OSClient.OSClientV3)) {
            throw new IllegalArgumentException("Only Identity API V3 is supported");
        }
        osV3Client = (OSClient.OSClientV3) client;
    }
}
