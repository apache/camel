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
package org.apache.camel.component.redis;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.URISupport;

/**
 * The Redis producer.
 */
public class RedisProducer extends DefaultProducer {
    private final RedisClient redisClient;

    private transient String redisProducerToString;
    
    public RedisProducer(RedisEndpoint endpoint, RedisConfiguration configuration) {
        super(endpoint);
        redisClient = new RedisClient(configuration.getRedisTemplate());
    }

    public void process(final Exchange exchange) throws Exception {
        new CommandDispatcher(getConfiguration(), exchange).execute(redisClient);
    }

    protected RedisConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public RedisEndpoint getEndpoint() {
        return (RedisEndpoint)super.getEndpoint();
    }

    @Override
    public String toString() {
        if (redisProducerToString == null) {
            redisProducerToString = "RedisProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return redisProducerToString;
    }
}
