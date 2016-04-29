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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeExchangeException;

class CommandDispatcher {
    private final RedisConfiguration configuration;

    CommandDispatcher(RedisConfiguration configuration) {
        this.configuration = configuration;
    }

    // CHECKSTYLE:OFF
    public void execute(final RedisClient redisClient, Exchange exchange) {
        final Command command = determineCommand(exchange);
        switch (command) {

        case PING:
            setResult(exchange, redisClient.ping());
            break;
        case SET:
            redisClient.set(getKey(exchange), getValue(exchange));
            break;
        case GET:
            setResult(exchange, redisClient.get(getKey(exchange)));
            break;
        case QUIT:
            redisClient.quit();
            break;
        case EXISTS:
            setResult(exchange, redisClient.exists(getKey(exchange)));
            break;
        case DEL:
            redisClient.del(getKeys(exchange));
            break;
        case TYPE:
            setResult(exchange, redisClient.type(getKey(exchange)));
            break;
        case KEYS:
            setResult(exchange, redisClient.keys(getPattern(exchange)));
            break;
        case RANDOMKEY:
            setResult(exchange, redisClient.randomkey());
            break;
        case RENAME:
            redisClient.rename(getKey(exchange), getStringValue(exchange));
            break;
        case RENAMENX:
            setResult(exchange, redisClient.renamenx(getKey(exchange), getStringValue(exchange)));
            break;
        case EXPIRE:
            setResult(exchange, redisClient.expire(getKey(exchange), getTimeout(exchange)));
            break;
        case EXPIREAT:
            setResult(exchange, redisClient.expireat(getKey(exchange), getTimestamp(exchange)));
            break;
        case PEXPIRE:
            setResult(exchange, redisClient.pexpire(getKey(exchange), getTimeout(exchange)));
            break;
        case PEXPIREAT:
            setResult(exchange, redisClient.pexpireat(getKey(exchange), getTimestamp(exchange)));
            break;
        case TTL:
            setResult(exchange, redisClient.ttl(getKey(exchange)));
            break;
        case MOVE:
            setResult(exchange, redisClient.move(getKey(exchange), getDb(exchange)));
            break;
        case GETSET:
            setResult(exchange, redisClient.getset(getKey(exchange), getValue(exchange)));
            break;
        case MGET:
            setResult(exchange, redisClient.mget(getFields(exchange)));
            break;
        case SETNX:
            setResult(exchange, redisClient.setnx(getKey(exchange), getValue(exchange)));
            break;
        case SETEX:
            redisClient.setex(getKey(exchange), getValue(exchange), getTimeout(exchange), TimeUnit.SECONDS);
            break;
        case MSET:
            redisClient.mset(getValuesAsMap(exchange));
            break;
        case MSETNX:
            redisClient.msetnx(getValuesAsMap(exchange));
            break;
        case DECRBY:
            setResult(exchange, redisClient.decrby(getKey(exchange), getLongValue(exchange)));
            break;
        case DECR:
            setResult(exchange, redisClient.decr(getKey(exchange)));
            break;
        case INCRBY:
            setResult(exchange, redisClient.incrby(getKey(exchange), getLongValue(exchange)));
            break;
        case INCR:
            setResult(exchange, redisClient.incr(getKey(exchange)));
            break;
        case APPEND:
            setResult(exchange, redisClient.append(getKey(exchange), getStringValue(exchange)));
            break;
        case HSET:
            redisClient.hset(getKey(exchange), getField(exchange), getValue(exchange));
            break;
        case HGET:
            setResult(exchange, redisClient.hget(getKey(exchange), getField(exchange)));
            break;
        case HSETNX:
            setResult(exchange, redisClient.hsetnx(getKey(exchange), getField(exchange), getValue(exchange)));
            break;
        case HMSET:
            redisClient.hmset(getKey(exchange), getValuesAsMap(exchange));
            break;
        case HMGET:
            setResult(exchange, redisClient.hmget(getKey(exchange), getFields(exchange)));
            break;
        case HINCRBY:
            setResult(exchange, redisClient.hincrBy(getKey(exchange), getField(exchange), getValueAsLong(exchange)));
            break;
        case HEXISTS:
            setResult(exchange, redisClient.hexists(getKey(exchange), getField(exchange)));
            break;
        case HDEL:
            redisClient.hdel(getKey(exchange), getField(exchange));
            break;
        case HLEN:
            setResult(exchange, redisClient.hlen(getKey(exchange)));
            break;
        case HKEYS:
            setResult(exchange, redisClient.hkeys(getKey(exchange)));
            break;
        case HVALS:
            setResult(exchange, redisClient.hvals(getKey(exchange)));
            break;
        case HGETALL:
            setResult(exchange, redisClient.hgetAll(getKey(exchange)));
            break;
        case RPUSH:
            setResult(exchange, redisClient.rpush(getKey(exchange), getValue(exchange)));
            break;
        case LPUSH:
            setResult(exchange, redisClient.lpush(getKey(exchange), getValue(exchange)));
            break;
        case LLEN:
            setResult(exchange, redisClient.llen(getKey(exchange)));
            break;
        case LRANGE:
            setResult(exchange, redisClient.lrange(getKey(exchange), getStart(exchange), getEnd(exchange)));
            break;
        case LTRIM:
            redisClient.ltrim(getKey(exchange), getStart(exchange), getEnd(exchange));
            break;
        case LINDEX:
            setResult(exchange, redisClient.lindex(getKey(exchange), getIndex(exchange)));
            break;
        case LSET:
            redisClient.lset(getKey(exchange), getValue(exchange), getIndex(exchange));
            break;
        case LREM:
            setResult(exchange, redisClient.lrem(getKey(exchange), getValue(exchange), getCount(exchange)));
            break;
        case LPOP:
            setResult(exchange, redisClient.lpop(getKey(exchange)));
            break;
        case RPOP:
            setResult(exchange, redisClient.rpop(getKey(exchange)));
            break;
        case RPOPLPUSH:
            setResult(exchange, redisClient.rpoplpush(getKey(exchange), getDestination(exchange)));
            break;
        case SADD:
            setResult(exchange, redisClient.sadd(getKey(exchange), getValue(exchange)));
            break;
        case SMEMBERS:
            setResult(exchange, redisClient.smembers(getKey(exchange)));
            break;
        case SREM:
            setResult(exchange, redisClient.srem(getKey(exchange), getValue(exchange)));
            break;
        case SPOP:
            setResult(exchange, redisClient.spop(getKey(exchange)));
            break;
        case SMOVE:
            setResult(exchange, redisClient.smove(getKey(exchange), getValue(exchange), getDestination(exchange)));
            break;
        case SCARD:
            setResult(exchange, redisClient.scard(getKey(exchange)));
            break;
        case SISMEMBER:
            setResult(exchange, redisClient.sismember(getKey(exchange), getValue(exchange)));
            break;
        case SINTER:
            setResult(exchange, redisClient.sinter(getKey(exchange), getKeys(exchange)));
            break;
        case SINTERSTORE:
            redisClient.sinterstore(getKey(exchange), getKeys(exchange), getDestination(exchange));
            break;
        case SUNION:
            setResult(exchange, redisClient.sunion(getKey(exchange), getKeys(exchange)));
            break;
        case SUNIONSTORE:
            redisClient.sunionstore(getKey(exchange), getKeys(exchange), getDestination(exchange));
            break;
        case SDIFF:
            setResult(exchange, redisClient.sdiff(getKey(exchange), getKeys(exchange)));
            break;
        case SDIFFSTORE:
            redisClient.sdiffstore(getKey(exchange), getKeys(exchange), getDestination(exchange));
            break;
        case SRANDMEMBER:
            setResult(exchange, redisClient.srandmember(getKey(exchange)));
            break;
        case ZADD:
            setResult(exchange, redisClient.zadd(getKey(exchange), getValue(exchange), getScore(exchange)));
            break;
        case ZRANGE:
            setResult(exchange, redisClient.zrange(getKey(exchange), getStart(exchange), getEnd(exchange), getWithScore(exchange)));
            break;
        case ZREM:
            setResult(exchange, redisClient.zrem(getKey(exchange), getValue(exchange)));
            break;
        case ZINCRBY:
            setResult(exchange, redisClient.zincrby(getKey(exchange), getValue(exchange), getIncrement(exchange)));
            break;
        case ZRANK:
            setResult(exchange, redisClient.zrank(getKey(exchange), getValue(exchange)));
            break;
        case ZREVRANK:
            setResult(exchange, redisClient.zrevrank(getKey(exchange), getValue(exchange)));
            break;
        case ZREVRANGE:
            setResult(exchange, redisClient.zrevrange(getKey(exchange), getStart(exchange), getEnd(exchange), getWithScore(exchange)));
            break;
        case ZCARD:
            setResult(exchange, redisClient.zcard(getKey(exchange)));
            break;
        case MULTI:
            redisClient.multi();
            break;
        case DISCARD:
            redisClient.discard();
            break;
        case EXEC:
            redisClient.exec();
            break;
        case WATCH:
            redisClient.watch(getKeys(exchange));
            break;
        case UNWATCH:
            redisClient.unwatch();
            break;
        case SORT:
            setResult(exchange, redisClient.sort(getKey(exchange)));
            break;
        case BLPOP:
            setResult(exchange, redisClient.blpop(getKey(exchange), getTimeout(exchange)));
            break;
        case BRPOP:
            setResult(exchange, redisClient.brpop(getKey(exchange), getTimeout(exchange)));
            break;
        case PUBLISH:
            redisClient.publish(getChannel(exchange), getMessage(exchange));
            break;
        case ZCOUNT:
            setResult(exchange, redisClient.zcount(getKey(exchange), getMin(exchange), getMax(exchange)));
            break;
        case ZRANGEBYSCORE:
            setResult(exchange, redisClient.zrangebyscore(getKey(exchange), getMin(exchange), getMax(exchange)));
            break;
        case ZREVRANGEBYSCORE:
            setResult(exchange, redisClient.zrevrangebyscore(getKey(exchange), getMin(exchange), getMax(exchange)));
            break;
        case ZREMRANGEBYRANK:
            redisClient.zremrangebyrank(getKey(exchange), getStart(exchange), getEnd(exchange));
            break;
        case ZREMRANGEBYSCORE:
            redisClient.zremrangebyscore(getKey(exchange), getStart(exchange), getEnd(exchange));
            break;
        case ZUNIONSTORE:
            redisClient.zunionstore(getKey(exchange), getKeys(exchange), getDestination(exchange));
            break;
        case ZINTERSTORE:
            redisClient.zinterstore(getKey(exchange), getKeys(exchange), getDestination(exchange));
            break;
        case STRLEN:
            setResult(exchange, redisClient.strlen(getKey(exchange)));
            break;
        case PERSIST:
            setResult(exchange, redisClient.persist(getKey(exchange)));
            break;
        case RPUSHX:
            setResult(exchange, redisClient.rpushx(getKey(exchange), getValue(exchange)));
            break;
        case ECHO:
            setResult(exchange, redisClient.echo(getStringValue(exchange)));
            break;
        case LINSERT:
            setResult(exchange, redisClient.linsert(getKey(exchange), getValue(exchange), getPivot(exchange), getPosition(exchange)));
            break;
        case BRPOPLPUSH:
            setResult(exchange, redisClient.brpoplpush(getKey(exchange), getDestination(exchange), getTimeout(exchange)));
            break;
        case SETBIT:
            redisClient.setbit(getKey(exchange), getOffset(exchange), getBooleanValue(exchange));
            break;
        case GETBIT:
            setResult(exchange, redisClient.getbit(getKey(exchange), getOffset(exchange)));
            break;
        case SETRANGE:
            redisClient.setex(getKey(exchange), getValue(exchange), getOffset(exchange));
            break;
        case GETRANGE:
            setResult(exchange, redisClient.getrange(getKey(exchange), getStart(exchange), getEnd(exchange)));
            break;
        default:
            throw new RuntimeExchangeException("Unsupported command: " + command, exchange);
        }
    }
    // CHECKSTYLE:ON

    private Command determineCommand(Exchange exchange) {
        Command command = exchange.getIn().getHeader(RedisConstants.COMMAND, Command.class);
        if (command == null) {
            command = configuration.getCommand();
        }
        if (command == null) {
            command = Command.SET;
        }
        return command;
    }

    private static <T> T getInHeaderValue(Exchange exchange, String key, Class<T> aClass) {
        return exchange.getIn().getHeader(key, aClass);
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

    public String getDestination(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.DESTINATION, String.class);
    }

    private String getChannel(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.CHANNEL, String.class);
    }

    private Object getMessage(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.MESSAGE, Object.class);
    }

    public Long getIndex(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.INDEX, Long.class);
    }

    public String getPivot(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.PIVOT, String.class);
    }

    public String getPosition(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.POSITION, String.class);
    }

    public Long getCount(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.COUNT, Long.class);
    }

    private Long getStart(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.START, Long.class);
    }

    private Long getEnd(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.END, Long.class);
    }

    private Long getTimeout(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.TIMEOUT, Long.class);
    }

    private Long getOffset(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.OFFSET, Long.class);
    }

    private Long getValueAsLong(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.VALUE, Long.class);
    }

    private Collection<String> getFields(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.FIELDS, Collection.class);
    }

    private Map<String, Object> getValuesAsMap(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.VALUES, Map.class);
    }

    private String getKey(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.KEY, String.class);
    }

    public Collection<String> getKeys(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.KEYS, Collection.class);
    }

    private Object getValue(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.VALUE, Object.class);
    }

    private String getStringValue(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.VALUE, String.class);
    }

    private Long getLongValue(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.VALUE, Long.class);
    }

    private Boolean getBooleanValue(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.VALUE, Boolean.class);
    }

    private String getField(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.FIELD, String.class);
    }

    public Long getTimestamp(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.TIMESTAMP, Long.class);
    }

    public String getPattern(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.PATTERN, String.class);
    }

    public Integer getDb(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.DB, Integer.class);
    }

    public Double getScore(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.SCORE, Double.class);
    }

    public Double getMin(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.MIN, Double.class);
    }

    public Double getMax(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.MAX, Double.class);
    }

    public Double getIncrement(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.INCREMENT, Double.class);
    }

    public Boolean getWithScore(Exchange exchange) {
        return getInHeaderValue(exchange, RedisConstants.WITHSCORE, Boolean.class);
    }
}
