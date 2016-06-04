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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.HeaderSelectorProducer;

final class RedisProducer extends HeaderSelectorProducer {
    private final Map<String, Processor> processors = new HashMap<>();

    RedisProducer(Endpoint endpoint,
                         RedisClient redisClient,
                         String header,
                         String defaultHeaderValue,
                         ExchangeConverter exchangeConverter) {
        super(endpoint, header, defaultHeaderValue);
        //bind key commands
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

        //bind sorted set commands
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

        //bind sets commands
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

        //bind pubsub commands
        bind(Command.PUBLISH, exchange -> redisClient.publish(exchangeConverter.getChannel(exchange),
                exchangeConverter.getMessage(exchange)));
        //missing psubscribe, pubsub, punsubscribe, subscribe, unsubscribe
        //psubscribe, subscribe are used in consumer

        //create list commands
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
        bind(Command.RPOP, exchange -> setResult(exchange,
                redisClient.rpop(exchangeConverter.getKey(exchange))));
        bind(Command.RPOPLPUSH, wrap(exchange -> redisClient.rpoplpush(exchangeConverter.getKey(exchange),
                exchangeConverter.getDestination(exchange))));
        bind(Command.RPUSH, wrap(exchange -> redisClient.rpush(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));
        bind(Command.RPUSHX, wrap(exchange -> redisClient.rpushx(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange))));

        //bind hashes commands
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

        //bind connection commands
        bind(Command.ECHO, wrap(exchange -> redisClient.echo(exchangeConverter.getStringValue(exchange))));
        bind(Command.PING, wrap(exchange -> redisClient.ping()));
        bind(Command.QUIT, exchange -> redisClient.quit());

        //bind key commands
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

        //bind transaction commands
        bind(Command.DISCARD, exchange -> redisClient.discard());
        bind(Command.EXEC, exchange -> redisClient.exec());
        bind(Command.MULTI, exchange -> redisClient.multi());
        bind(Command.WATCH, exchange -> redisClient.watch(exchangeConverter.getKeys(exchange)));
        bind(Command.UNWATCH, exchange -> redisClient.unwatch());
    }

    private void bind(Command command, Processor processor) {
        String cmd = command.name();
        bind(cmd, processor);
    }


    private void setResult(Exchange exchange, Object result) {
        Message message;
        if (exchange.getPattern().isOutCapable()) {
            message = exchange.getOut();
            message.copyFrom(exchange.getIn());
        } else {
            message = exchange.getIn();
        }
        message.setBody(result);
    }

    public Processor wrap(Function<Exchange, Object> supplier) {
        return exchange -> {
            Object result = supplier.apply(exchange);
            setResult(exchange, result);
        };
    }

}
