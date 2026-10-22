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
package org.apache.camel.component.redis;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Send and receive messages from Redis.
 */
@UriEndpoint(firstVersion = "2.11.0", scheme = "spring-redis", title = "Spring Redis", syntax = "spring-redist:host:port",
             category = { Category.CACHE }, headersClass = RedisConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.UNKNOWN,
                                   description = "The message as the serializer reads it: with the default JDK serializer the"
                                                 + " Java object the publisher serialized, such as the String a PUBLISH of this"
                                                 + " component sent, with the channel in the CamelRedis.Channel header. A"
                                                 + " message that is not Java serialized, such as one from redis-cli, is"
                                                 + " dropped with a WARN: set a StringRedisSerializer as the serializer to"
                                                 + " receive text"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The body is not used: the key, the value and the other arguments of the"
                                                + " command come from headers, such as CamelRedis.Key and CamelRedis.Value, or"
                                                + " CamelRedis.Channel and CamelRedis.Message for PUBLISH. Set CamelRedis.Value"
                                                + " from the body to store the body: with the default serializer it must be"
                                                + " Serializable, so a stream fails"),
           producerOut = @BodyType(value = Carrier.UNCHANGED, dependsOn = "command",
                                   byOption = {
                                           @BodyType.When(option = "command", value = "SET", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "command", value = "SETEX", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "command", value = "MSET", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "command", value = "HSET", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "command", value = "HMSET", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "command", value = "HDEL", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "command", value = "DEL", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "command", value = "PUBLISH", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "command", value = "LSET", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "command", value = "LTRIM", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "command", value = "RENAME", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "command", value = "EXISTS", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "command", value = "EXPIRE", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "command", value = "SETNX", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "command", value = "HEXISTS", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "command", value = "SISMEMBER", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "command", value = "INCR", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "command", value = "INCRBY", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "command", value = "DECR", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "command", value = "DECRBY", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "command", value = "LPUSH", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "command", value = "RPUSH", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "command", value = "LLEN", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "command", value = "SADD", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "command", value = "SREM", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "command", value = "SCARD", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "command", value = "HLEN", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "command", value = "TTL", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "command", value = "PING", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "command", value = "ECHO", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "command", value = "HGETALL", carriers = Carrier.MAP),
                                           @BodyType.When(option = "command", value = "KEYS", carriers = Carrier.LIST),
                                           @BodyType.When(option = "command", value = "HKEYS", carriers = Carrier.LIST),
                                           @BodyType.When(option = "command", value = "HVALS", carriers = Carrier.LIST),
                                           @BodyType.When(option = "command", value = "SMEMBERS", carriers = Carrier.LIST),
                                           @BodyType.When(option = "command", value = "ZRANGE", carriers = Carrier.LIST),
                                           @BodyType.When(option = "command", value = "ZREVRANGE", carriers = Carrier.LIST),
                                           @BodyType.When(option = "command", value = "LRANGE", carriers = Carrier.LIST),
                                           @BodyType.When(option = "command", value = "MGET", carriers = Carrier.LIST),
                                           @BodyType.When(option = "command", value = "HMGET", carriers = Carrier.LIST) },
                                   description = "The result of the command, which the CamelRedis.Command header can pick"
                                                 + " instead of the command option: SET, the default, PUBLISH and the other"
                                                 + " commands without a result keep the body; a Boolean or a number for those"
                                                 + " that test, count or increment, a Set or a List for those that read several"
                                                 + " members, a Map for HGETALL, and the stored object for GET and the commands"
                                                 + " that read or pop one value. A null result, such as GET of a missing key,"
                                                 + " keeps the body as well"))
public class RedisEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private final RedisConfiguration configuration;

    public RedisEndpoint(String uri, RedisComponent component, RedisConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        Command defaultCommand = configuration.getCommand();
        if (defaultCommand == null) {
            defaultCommand = Command.SET;
        }

        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = (RedisTemplate<String, Object>) configuration.getRedisTemplate();
        return new RedisProducer(
                this, RedisConstants.COMMAND, defaultCommand.name(), new RedisClient(redisTemplate));
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        RedisConsumer answer = new RedisConsumer(this, processor, configuration);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getHost() + ":" + configuration.getPort();
    }

    @Override
    public String getServiceProtocol() {
        return "redis";
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        configuration.stop();
    }

    public RedisConfiguration getConfiguration() {
        return configuration;
    }
}
