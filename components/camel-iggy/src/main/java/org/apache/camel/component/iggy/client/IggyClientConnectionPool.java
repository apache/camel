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
package org.apache.camel.component.iggy.client;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.iggy.client.blocking.IggyBaseClient;

public class IggyClientConnectionPool {
    private final GenericObjectPool<IggyBaseClient> pool;

    public IggyClientConnectionPool(String host, int port, String username, String password, String transport) {
        IggyClientFactory factory = new IggyClientFactory(host, port, username, password, transport);
        this.pool = new GenericObjectPool<>(factory);
    }

    public IggyBaseClient borrowObject() throws Exception {
        return pool.borrowObject();
    }

    public void returnClient(IggyBaseClient client) {
        pool.returnObject(client);
    }

    public int getNumActive() {
        return pool.getNumActive();
    }

    public int getNumIdle() {
        return pool.getNumIdle();
    }
}
