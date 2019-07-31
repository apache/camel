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

public final class ListsRedisProcessorsCreator extends AbstractRedisProcessorCreator {

    Map<Command, Processor> getProcessors(RedisClient redisClient, ExchangeConverter exchangeConverter) {
        bind(Command.BLPOP, wrap(exchange -> redisClient.blpop(exchangeConverter.getKey(exchange),
                exchangeConverter.getTimeout(exchange))));
        bind(Command.BRPOP, wrap(exchange -> redisClient.brpop(exchangeConverter.getKey(exchange),
                exchangeConverter.getTimeout(exchange))));
        bind(Command.BRPOPLPUSH, wrap(exchange -> redisClient.brpoplpush(exchangeConverter.getKey(exchange),
                exchangeConverter.getDestination(exchange),
                exchangeConverter.getTimeout(exchange))));
        bind(Command.LINDEX, wrap(exchange -> redisClient.lindex(exchangeConverter.getKey(exchange),
                exchangeConverter.getIndex(exchange))));
        bind(Command.LINSERT, wrap(exchange -> redisClient.linsert(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange),
                exchangeConverter.getPivot(exchange),
                exchangeConverter.getPosition(exchange))));
        bind(Command.LLEN, wrap(exchange -> redisClient.llen(exchangeConverter.getKey(exchange))));
        bind(Command.LPOP, wrap(exchange -> redisClient.lpop(exchangeConverter.getKey(exchange))));
        bind(Command.LPUSH, wrap(exchange -> redisClient.lpush(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));
        //nieuwe actie
        bind(Command.LPUSHX, wrap(exchange -> redisClient.lpushx(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));
        bind(Command.LRANGE, wrap(exchange -> redisClient.lrange(exchangeConverter.getKey(exchange),
                exchangeConverter.getStart(exchange),
                exchangeConverter.getEnd(exchange))));
        bind(Command.LREM, wrap(exchange -> redisClient.lrem(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange),
                exchangeConverter.getCount(exchange))));
        bind(Command.LSET, exchange -> redisClient.lset(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange),
                exchangeConverter.getIndex(exchange)));
        bind(Command.LTRIM, exchange -> redisClient.ltrim(exchangeConverter.getKey(exchange),
                exchangeConverter.getStart(exchange),
                exchangeConverter.getEnd(exchange)));
        bind(Command.RPOP, wrap(exchange ->
                redisClient.rpop(exchangeConverter.getKey(exchange))));
        bind(Command.RPOPLPUSH, wrap(exchange -> redisClient.rpoplpush(exchangeConverter.getKey(exchange),
                exchangeConverter.getDestination(exchange))));
        bind(Command.RPUSH, wrap(exchange -> redisClient.rpush(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));
        bind(Command.RPUSHX, wrap(exchange -> redisClient.rpushx(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));

        return result;
    }

}
