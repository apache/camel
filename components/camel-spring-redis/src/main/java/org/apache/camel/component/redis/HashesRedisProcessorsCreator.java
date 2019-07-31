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

import java.util.Map;

import org.apache.camel.Processor;

public final class HashesRedisProcessorsCreator extends AbstractRedisProcessorCreator {

    Map<Command, Processor> getProcessors(RedisClient redisClient, ExchangeConverter exchangeConverter) {
        bind(Command.HDEL, exchange -> redisClient.hdel(exchangeConverter.getKey(exchange),
                exchangeConverter.getField(exchange)));
        bind(Command.HEXISTS, wrap(exchange -> redisClient.hexists(exchangeConverter.getKey(exchange),
                exchangeConverter.getField(exchange))));
        bind(Command.HGET, wrap(exchange -> redisClient.hget(exchangeConverter.getKey(exchange),
                exchangeConverter.getField(exchange))));
        bind(Command.HGETALL, wrap(exchange -> redisClient.hgetAll(exchangeConverter.getKey(exchange))));
        bind(Command.HINCRBY, wrap(exchange -> redisClient.hincrBy(exchangeConverter.getKey(exchange),
                exchangeConverter.getField(exchange),
                exchangeConverter.getValueAsLong(exchange))));
        bind(Command.HKEYS, wrap(exchange -> redisClient.hkeys(exchangeConverter.getKey(exchange))));
        bind(Command.HLEN, wrap(exchange -> redisClient.hlen(exchangeConverter.getKey(exchange))));
        bind(Command.HMGET, wrap(exchange -> redisClient.hmget(exchangeConverter.getKey(exchange),
                exchangeConverter.getFields(exchange))));
        bind(Command.HMSET, exchange -> redisClient.hmset(exchangeConverter.getKey(exchange),
                exchangeConverter.getValuesAsMap(exchange)));
        bind(Command.HSET, exchange -> redisClient.hset(exchangeConverter.getKey(exchange),
                exchangeConverter.getField(exchange),
                exchangeConverter.getValue(exchange)));
        bind(Command.HSETNX, wrap(exchange -> redisClient.hsetnx(exchangeConverter.getKey(exchange),
                exchangeConverter.getField(exchange),
                exchangeConverter.getValue(exchange))));
        bind(Command.HVALS, wrap(exchange -> redisClient.hvals(exchangeConverter.getKey(exchange))));
        //missing: hincrbyfloat, hstrlen, hscan

        return result;
    }

}
