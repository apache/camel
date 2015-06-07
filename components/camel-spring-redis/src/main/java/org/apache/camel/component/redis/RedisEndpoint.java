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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Represents a Redis endpoint.
 */
@UriEndpoint(scheme = "spring-redis", title = "Spring Redis", syntax = "spring-redist:host:port", consumerClass = RedisConsumer.class, label = "spring,nosql")
public class RedisEndpoint extends DefaultEndpoint {

    @UriParam
    private RedisConfiguration configuration;

    public RedisEndpoint(String uri, RedisComponent component, RedisConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() throws Exception {
        return new RedisProducer(this, configuration);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        RedisConsumer answer = new RedisConsumer(this, processor, configuration);
        configureConsumer(answer);
        return answer;
    }

    public boolean isSingleton() {
        return true;
    }

    protected void doShutdown() throws Exception {
        super.doShutdown();
        configuration.stop();
    }

    public RedisConfiguration getConfiguration() {
        return configuration;
    }
}
