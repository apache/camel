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
package org.apache.camel.component.rabbitmq.pool;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.apache.commons.pool.PoolableObjectFactory;

/**
 * Channel lifecycle manager: create, check and close channel
 */
public class PoolableChannelFactory implements PoolableObjectFactory<Channel> {

    /**
     * Parent connection
     */
    private final Connection connection;

    public PoolableChannelFactory(Connection connection) {
        this.connection = connection;
    }
    
    @Override
    public Channel makeObject() throws Exception {
        return connection.createChannel();
    }

    @Override
    public void destroyObject(Channel t) throws Exception {
        try {
            t.close();
        } catch (Exception e) {
            //no-op
        }
    }

    @Override
    public boolean validateObject(Channel t) {
        return t.isOpen();
    }

    @Override
    public void activateObject(Channel t) throws Exception {
    }

    @Override
    public void passivateObject(Channel t) throws Exception {
    }

}
