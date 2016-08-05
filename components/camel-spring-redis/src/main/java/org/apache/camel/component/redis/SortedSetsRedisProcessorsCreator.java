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

import java.util.Map;

import org.apache.camel.Processor;

public final class SortedSetsRedisProcessorsCreator extends AbstractRedisProcessorCreator {

    Map<Command, Processor> getProcessors(RedisClient redisClient, ExchangeConverter exchangeConverter) {
        bind(Command.ZADD, wrap(exchange -> redisClient.zadd(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange),
                exchangeConverter.getScore(exchange))));
        bind(Command.ZCARD, wrap(exchange -> redisClient.zcard(exchangeConverter.getKey(exchange))));
        bind(Command.ZCOUNT, wrap(exchange -> redisClient.zcount(exchangeConverter.getKey(exchange),
                exchangeConverter.getMin(exchange),
                exchangeConverter.getMax(exchange))));
        bind(Command.ZINCRBY, wrap(exchange -> redisClient.zincrby(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange),
                exchangeConverter.getIncrement(exchange))));
        bind(Command.ZINTERSTORE, exchange -> redisClient.zinterstore(exchangeConverter.getKey(exchange),
                exchangeConverter.getKeys(exchange),
                exchangeConverter.getDestination(exchange)));
        bind(Command.ZRANGE, wrap(exchange -> redisClient.zrange(exchangeConverter.getKey(exchange),
                exchangeConverter.getStart(exchange),
                exchangeConverter.getEnd(exchange),
                exchangeConverter.getWithScore(exchange))));
        bind(Command.ZRANGEBYSCORE, wrap(exchange -> redisClient.zrangebyscore(exchangeConverter.getKey(exchange),
                exchangeConverter.getMin(exchange),
                exchangeConverter.getMax(exchange))));
        bind(Command.ZRANK, wrap(exchange -> redisClient.zrank(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));
        bind(Command.ZREM, wrap(exchange -> redisClient.zrem(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));
        bind(Command.ZREMRANGEBYRANK, exchange -> redisClient.zremrangebyrank(exchangeConverter.getKey(exchange),
                exchangeConverter.getStart(exchange),
                exchangeConverter.getEnd(exchange)));
        bind(Command.ZREMRANGEBYSCORE, exchange -> redisClient.zremrangebyscore(exchangeConverter.getKey(exchange),
                exchangeConverter.getStart(exchange),
                exchangeConverter.getEnd(exchange)));
        bind(Command.ZREVRANGE, wrap(exchange -> redisClient.zrevrange(exchangeConverter.getKey(exchange),
                exchangeConverter.getStart(exchange),
                exchangeConverter.getEnd(exchange),
                exchangeConverter.getWithScore(exchange))));
        bind(Command.ZREVRANGEBYSCORE, wrap(exchange -> redisClient.zrevrangebyscore(exchangeConverter.getKey(exchange),
                exchangeConverter.getMin(exchange),
                exchangeConverter.getMax(exchange))));
        bind(Command.ZREVRANK, wrap(exchange -> redisClient.zrevrank(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));
        bind(Command.ZUNIONSTORE, exchange -> redisClient.zunionstore(exchangeConverter.getKey(exchange),
                exchangeConverter.getKeys(exchange),
                exchangeConverter.getDestination(exchange)));
        //missing zlexcount, zrangebylex, zrevrangebylex, zremrangebylex, zscore, zscan

        return result;
    }

}
