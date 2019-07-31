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

public final class SetsRedisProcessorsCreator extends AbstractRedisProcessorCreator {

    Map<Command, Processor> getProcessors(RedisClient redisClient, ExchangeConverter exchangeConverter) {
        bind(Command.SADD, wrap(exchange -> redisClient.sadd(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));
        bind(Command.SCARD, wrap(exchange -> redisClient.scard(exchangeConverter.getKey(exchange))));
        bind(Command.SDIFF, wrap(exchange -> redisClient.sdiff(exchangeConverter.getKey(exchange),
                exchangeConverter.getKeys(exchange))));
        bind(Command.SDIFFSTORE, exchange -> redisClient.sdiffstore(exchangeConverter.getKey(exchange),
                exchangeConverter.getKeys(exchange),
                exchangeConverter.getDestination(exchange)));
        bind(Command.SINTER, wrap(exchange -> redisClient.sinter(exchangeConverter.getKey(exchange),
                exchangeConverter.getKeys(exchange))));
        bind(Command.SINTERSTORE, exchange -> redisClient.sinterstore(exchangeConverter.getKey(exchange),
                exchangeConverter.getKeys(exchange),
                exchangeConverter.getDestination(exchange)));
        bind(Command.SISMEMBER, wrap(exchange -> redisClient.sismember(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));
        bind(Command.SMEMBERS, wrap(exchange -> redisClient.smembers(exchangeConverter.getKey(exchange))));
        bind(Command.SMOVE, wrap(exchange -> redisClient.smove(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange),
                exchangeConverter.getDestination(exchange))));
        bind(Command.SPOP, wrap(exchange -> redisClient.spop(exchangeConverter.getKey(exchange))));
        bind(Command.SRANDMEMBER, wrap(exchange -> redisClient.srandmember(exchangeConverter.getKey(exchange))));
        bind(Command.SREM, wrap(exchange -> redisClient.srem(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));
        bind(Command.SUNION, wrap(exchange -> redisClient.sunion(exchangeConverter.getKey(exchange),
                exchangeConverter.getKeys(exchange))));
        bind(Command.SUNIONSTORE, exchange -> redisClient.sunionstore(exchangeConverter.getKey(exchange),
                exchangeConverter.getKeys(exchange),
                exchangeConverter.getDestination(exchange)));
        //missing command sscan

        return result;
    }

}
