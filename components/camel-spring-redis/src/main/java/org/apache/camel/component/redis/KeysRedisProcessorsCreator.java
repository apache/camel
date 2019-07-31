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

public final class KeysRedisProcessorsCreator extends AbstractRedisProcessorCreator {

    Map<Command, Processor> getProcessors(RedisClient redisClient, ExchangeConverter exchangeConverter) {
        bind(Command.DEL, exchange -> redisClient.del(exchangeConverter.getKeys(exchange)));
        bind(Command.EXISTS, wrap(exchange -> redisClient.exists(exchangeConverter.getKey(exchange))));
        bind(Command.EXPIRE, wrap(exchange -> redisClient.expire(exchangeConverter.getKey(exchange),
                exchangeConverter.getTimeout(exchange))));
        bind(Command.EXPIREAT, wrap(exchange -> redisClient.expireat(exchangeConverter.getKey(exchange),
                exchangeConverter.getTimestamp(exchange))));
        bind(Command.KEYS, wrap(exchange -> redisClient.keys(exchangeConverter.getPattern(exchange))));
        bind(Command.MOVE, wrap(exchange -> redisClient.move(exchangeConverter.getKey(exchange),
                exchangeConverter.getDb(exchange))));
        bind(Command.PERSIST, wrap(exchange -> redisClient.persist(exchangeConverter.getKey(exchange))));
        bind(Command.PEXPIRE, wrap(exchange -> redisClient.pexpire(exchangeConverter.getKey(exchange),
                exchangeConverter.getTimeout(exchange))));
        bind(Command.PEXPIREAT, wrap(exchange -> redisClient.pexpireat(exchangeConverter.getKey(exchange),
                exchangeConverter.getTimestamp(exchange))));
        bind(Command.RANDOMKEY, wrap(exchange -> redisClient.randomkey()));
        bind(Command.RENAME, exchange -> redisClient.rename(exchangeConverter.getKey(exchange),
                exchangeConverter.getStringValue(exchange)));
        bind(Command.RENAMENX, wrap(exchange -> redisClient.renamenx(exchangeConverter.getKey(exchange),
                exchangeConverter.getStringValue(exchange))));
        bind(Command.SORT, wrap(exchange -> redisClient.sort(exchangeConverter.getKey(exchange))));
        bind(Command.TTL, wrap(exchange -> redisClient.ttl(exchangeConverter.getKey(exchange))));
        bind(Command.TYPE, wrap(exchange -> redisClient.type(exchangeConverter.getKey(exchange))));
        //missing: dump, migrate, object, pttl, restore, wait, scan

        return result;
    }

}
