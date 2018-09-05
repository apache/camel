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
package org.apache.camel.component.atomix.client;

import io.atomix.AtomixClient;
import io.atomix.AtomixReplica;
import io.atomix.catalyst.transport.Address;
import org.apache.camel.test.spring.CamelSpringTestSupport;

public abstract class AtomixClientSpringTestSupport extends CamelSpringTestSupport {
    protected Address getReplicaAddress() {
        return applicationContext.getBean(Address.class);
    }

    protected AtomixReplica getReplica() {
        return applicationContext.getBean(AtomixReplica.class);
    }

    protected AtomixClient getClient() {
        return applicationContext.getBean(AtomixClient.class);
    }
}
