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
import java.util.concurrent.TimeUnit;

import org.apache.camel.Processor;

public final class StringsRedisProcessorsCreator extends AbstractRedisProcessorCreator {

    Map<Command, Processor> getProcessors(RedisClient redisClient, ExchangeConverter exchangeConverter) {
        bind(Command.APPEND, wrap(exchange -> redisClient.append(exchangeConverter.getKey(exchange),
                exchangeConverter.getStringValue(exchange))));
        bind(Command.DECR, wrap(exchange -> redisClient.decr(exchangeConverter.getKey(exchange))));
        bind(Command.DECRBY, wrap(exchange -> redisClient.decrby(exchangeConverter.getKey(exchange),
                exchangeConverter.getLongValue(exchange))));
        bind(Command.GET, wrap(exchange -> redisClient.get(exchangeConverter.getKey(exchange))));
        bind(Command.GETBIT, wrap(exchange -> redisClient.getbit(exchangeConverter.getKey(exchange),
                exchangeConverter.getOffset(exchange))));
        bind(Command.GETRANGE, wrap(exchange -> redisClient.getrange(exchangeConverter.getKey(exchange),
                exchangeConverter.getStart(exchange),
                exchangeConverter.getEnd(exchange))));
        bind(Command.GETSET, wrap(exchange -> redisClient.getset(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));
        bind(Command.INCR, wrap(exchange -> redisClient.incr(exchangeConverter.getKey(exchange))));
        bind(Command.INCRBY, wrap(exchange -> redisClient.incrby(exchangeConverter.getKey(exchange),
                exchangeConverter.getLongValue(exchange))));
        bind(Command.MGET, wrap(exchange -> redisClient.mget(exchangeConverter.getFields(exchange))));
        bind(Command.MSET, exchange -> redisClient.mset(exchangeConverter.getValuesAsMap(exchange)));
        bind(Command.MSETNX, exchange -> redisClient.msetnx(exchangeConverter.getValuesAsMap(exchange)));
        bind(Command.SET, exchange -> redisClient.set(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange)));
        bind(Command.SETBIT, exchange -> redisClient.setbit(exchangeConverter.getKey(exchange),
                exchangeConverter.getOffset(exchange),
                exchangeConverter.getBooleanValue(exchange)));
        bind(Command.SETEX, exchange -> redisClient.setex(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange),
                exchangeConverter.getTimeout(exchange),
                TimeUnit.SECONDS));
        bind(Command.SETNX, wrap(exchange -> redisClient.setnx(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));
        bind(Command.SETRANGE, exchange -> redisClient.setex(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange),
                exchangeConverter.getOffset(exchange)));
        bind(Command.STRLEN, wrap(exchange -> redisClient.strlen(exchangeConverter.getKey(exchange))));
        //missing bitcount, bitfield, bitop, bitpos, incrbyfloat, psetex

        return result;
    }

}
