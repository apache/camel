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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeExchangeException;

public class CommandDispatcher {
    private final RedisConfiguration configuration;
    private final Exchange exchange;

    public CommandDispatcher(RedisConfiguration configuration, Exchange exchange) {
        this.configuration = configuration;
        this.exchange = exchange;
    }

    // CHECKSTYLE:OFF
    public void execute(final RedisClient redisClient) {
        final Command command = determineCommand();
        switch (command) {

        case PING:
            setResult(redisClient.ping());
            break;
        case SET:
            redisClient.set(getKey(), getValue());
            break;
        case GET:
            setResult(redisClient.get(getKey()));
            break;
        case QUIT:
            redisClient.quit();
            break;
        case EXISTS:
            setResult(redisClient.exists(getKey()));
            break;
        case DEL:
            redisClient.del(getKeys());
            break;
        case TYPE:
            setResult(redisClient.type(getKey()));
            break;
        case KEYS:
            setResult(redisClient.keys(getPattern()));
            break;
        case RANDOMKEY:
            setResult(redisClient.randomkey());
            break;
        case RENAME:
            redisClient.rename(getKey(), getStringValue());
            break;
        case RENAMENX:
            setResult(redisClient.renamenx(getKey(), getStringValue()));
            break;
        case EXPIRE:
            setResult(redisClient.expire(getKey(), getTimeout()));
            break;
        case EXPIREAT:
            setResult(redisClient.expireat(getKey(), getTimestamp()));
            break;
        case PEXPIRE:
            setResult(redisClient.pexpire(getKey(), getTimeout()));
            break;
        case PEXPIREAT:
            setResult(redisClient.pexpireat(getKey(), getTimestamp()));
            break;
        case TTL:
            setResult(redisClient.ttl(getKey()));
            break;
        case MOVE:
            setResult(redisClient.move(getKey(), getDb()));
            break;
        case GETSET:
            setResult(redisClient.getset(getKey(), getValue()));
            break;
        case MGET:
            setResult(redisClient.mget(getFields()));
            break;
        case SETNX:
            setResult(redisClient.setnx(getKey(), getValue()));
            break;
        case SETEX:
            redisClient.setex(getKey(), getValue(), getTimeout(), TimeUnit.SECONDS);
            break;
        case MSET:
            redisClient.mset(getValuesAsMap());
            break;
        case MSETNX:
            redisClient.msetnx(getValuesAsMap());
            break;
        case DECRBY:
            setResult(redisClient.decrby(getKey(), getLongValue()));
            break;
        case DECR:
            setResult(redisClient.decr(getKey()));
            break;
        case INCRBY:
            setResult(redisClient.incrby(getKey(), getLongValue()));
            break;
        case INCR:
            setResult(redisClient.incr(getKey()));
            break;
        case APPEND:
            setResult(redisClient.append(getKey(), getStringValue()));
            break;
        case HSET:
            redisClient.hset(getKey(), getField(), getValue());
            break;
        case HGET:
            setResult(redisClient.hget(getKey(), getField()));
            break;
        case HSETNX:
            setResult(redisClient.hsetnx(getKey(), getField(), getValue()));
            break;
        case HMSET:
            redisClient.hmset(getKey(), getValuesAsMap());
            break;
        case HMGET:
            setResult(redisClient.hmget(getKey(), getFields()));
            break;
        case HINCRBY:
            setResult(redisClient.hincrBy(getKey(), getField(), getValueAsLong()));
            break;
        case HEXISTS:
            setResult(redisClient.hexists(getKey(), getField()));
            break;
        case HDEL:
            redisClient.hdel(getKey(), getField());
            break;
        case HLEN:
            setResult(redisClient.hlen(getKey()));
            break;
        case HKEYS:
            setResult(redisClient.hkeys(getKey()));
            break;
        case HVALS:
            setResult(redisClient.hvals(getKey()));
            break;
        case HGETALL:
            setResult(redisClient.hgetAll(getKey()));
            break;
        case RPUSH:
            setResult(redisClient.rpush(getKey(), getValue()));
            break;
        case LPUSH:
            setResult(redisClient.lpush(getKey(), getValue()));
            break;
        case LLEN:
            setResult(redisClient.llen(getKey()));
            break;
        case LRANGE:
            setResult(redisClient.lrange(getKey(), getStart(), getEnd()));
            break;
        case LTRIM:
            redisClient.ltrim(getKey(), getStart(), getEnd());
            break;
        case LINDEX:
            setResult(redisClient.lindex(getKey(), getIndex()));
            break;
        case LSET:
            redisClient.lset(getKey(), getValue(), getIndex());
            break;
        case LREM:
            setResult(redisClient.lrem(getKey(), getValue(), getCount()));
            break;
        case LPOP:
            setResult(redisClient.lpop(getKey()));
            break;
        case RPOP:
            setResult(redisClient.rpop(getKey()));
            break;
        case RPOPLPUSH:
            setResult(redisClient.rpoplpush(getKey(), getDestination()));
            break;
        case SADD:
            setResult(redisClient.sadd(getKey(), getValue()));
            break;
        case SMEMBERS:
            setResult(redisClient.smembers(getKey()));
            break;
        case SREM:
            setResult(redisClient.srem(getKey(), getValue()));
            break;
        case SPOP:
            setResult(redisClient.spop(getKey()));
            break;
        case SMOVE:
            setResult(redisClient.smove(getKey(), getValue(), getDestination()));
            break;
        case SCARD:
            setResult(redisClient.scard(getKey()));
            break;
        case SISMEMBER:
            setResult(redisClient.sismember(getKey(), getValue()));
            break;
        case SINTER:
            setResult(redisClient.sinter(getKey(), getKeys()));
            break;
        case SINTERSTORE:
            redisClient.sinterstore(getKey(), getKeys(), getDestination());
            break;
        case SUNION:
            setResult(redisClient.sunion(getKey(), getKeys()));
            break;
        case SUNIONSTORE:
            redisClient.sunionstore(getKey(), getKeys(), getDestination());
            break;
        case SDIFF:
            setResult(redisClient.sdiff(getKey(), getKeys()));
            break;
        case SDIFFSTORE:
            redisClient.sdiffstore(getKey(), getKeys(), getDestination());
            break;
        case SRANDMEMBER:
            setResult(redisClient.srandmember(getKey()));
            break;
        case ZADD:
            setResult(redisClient.zadd(getKey(), getValue(), getScore()));
            break;
        case ZRANGE:
            setResult(redisClient.zrange(getKey(), getStart(), getEnd(), getWithScore()));
            break;
        case ZREM:
            setResult(redisClient.zrem(getKey(), getValue()));
            break;
        case ZINCRBY:
            setResult(redisClient.zincrby(getKey(), getValue(), getIncrement()));
            break;
        case ZRANK:
            setResult(redisClient.zrank(getKey(), getValue()));
            break;
        case ZREVRANK:
            setResult(redisClient.zrevrank(getKey(), getValue()));
            break;
        case ZREVRANGE:
            setResult(redisClient.zrevrange(getKey(), getStart(), getEnd(), getWithScore()));
            break;
        case ZCARD:
            setResult(redisClient.zcard(getKey()));
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
            redisClient.watch(getKeys());
            break;
        case UNWATCH:
            redisClient.unwatch();
            break;
        case SORT:
            setResult(redisClient.sort(getKey()));
            break;
        case BLPOP:
            setResult(redisClient.blpop(getKey(), getTimeout()));
            break;
        case BRPOP:
            setResult(redisClient.brpop(getKey(), getTimeout()));
            break;
        case PUBLISH:
            redisClient.publish(getChannel(), getMessage());
            break;
        case ZCOUNT:
            setResult(redisClient.zcount(getKey(), getMin(), getMax()));
            break;
        case ZRANGEBYSCORE:
            setResult(redisClient.zrangebyscore(getKey(), getMin(), getMax()));
            break;
        case ZREVRANGEBYSCORE:
            setResult(redisClient.zrevrangebyscore(getKey(), getMin(), getMax()));
            break;
        case ZREMRANGEBYRANK:
            redisClient.zremrangebyrank(getKey(), getStart(), getEnd());
            break;
        case ZREMRANGEBYSCORE:
            redisClient.zremrangebyscore(getKey(), getStart(), getEnd());
            break;
        case ZUNIONSTORE:
            redisClient.zunionstore(getKey(), getKeys(), getDestination());
            break;
        case ZINTERSTORE:
            redisClient.zinterstore(getKey(), getKeys(), getDestination());
            break;
        case STRLEN:
            setResult(redisClient.strlen(getKey()));
            break;
        case PERSIST:
            setResult(redisClient.persist(getKey()));
            break;
        case RPUSHX:
            setResult(redisClient.rpushx(getKey(), getValue()));
            break;
        case ECHO:
            setResult(redisClient.echo(getStringValue()));
            break;
        case LINSERT:
            setResult(redisClient.linsert(getKey(), getValue(), getPivot(), getPosition()));
            break;
        case BRPOPLPUSH:
            setResult(redisClient.brpoplpush(getKey(), getDestination(), getTimeout()));
            break;
        case SETBIT:
            redisClient.setbit(getKey(), getOffset(), getBooleanValue());
            break;
        case GETBIT:
            setResult(redisClient.getbit(getKey(), getOffset()));
            break;
        case SETRANGE:
            redisClient.setex(getKey(), getValue(), getOffset());
            break;
        case GETRANGE:
            setResult(redisClient.getrange(getKey(), getStart(), getEnd()));
            break;
        default:
            throw new RuntimeExchangeException("Unsupported command: " + command, exchange);
        }
    }
    // CHECKSTYLE:ON

    private Command determineCommand() {
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

    private void setResult(Object result) {
        Message message;
        if (exchange.getPattern().isOutCapable()) {
            message = exchange.getOut();
            message.copyFrom(exchange.getIn());
        } else {
            message = exchange.getIn();
        }
        message.setBody(result);
    }

    public String getDestination() {
        return getInHeaderValue(exchange, RedisConstants.DESTINATION, String.class);
    }

    private String getChannel() {
        return getInHeaderValue(exchange, RedisConstants.CHANNEL, String.class);
    }

    private Object getMessage() {
        return getInHeaderValue(exchange, RedisConstants.MESSAGE, Object.class);
    }

    public Long getIndex() {
        return getInHeaderValue(exchange, RedisConstants.INDEX, Long.class);
    }

    public String getPivot() {
        return getInHeaderValue(exchange, RedisConstants.PIVOT, String.class);
    }

    public String getPosition() {
        return getInHeaderValue(exchange, RedisConstants.POSITION, String.class);
    }

    public Long getCount() {
        return getInHeaderValue(exchange, RedisConstants.COUNT, Long.class);
    }

    private Long getStart() {
        return getInHeaderValue(exchange, RedisConstants.START, Long.class);
    }

    private Long getEnd() {
        return getInHeaderValue(exchange, RedisConstants.END, Long.class);
    }

    private Long getTimeout() {
        return getInHeaderValue(exchange, RedisConstants.TIMEOUT, Long.class);
    }

    private Long getOffset() {
        return getInHeaderValue(exchange, RedisConstants.OFFSET, Long.class);
    }

    private Long getValueAsLong() {
        return getInHeaderValue(exchange, RedisConstants.VALUE, Long.class);
    }

    private Collection<String> getFields() {
        return getInHeaderValue(exchange, RedisConstants.FIELDS, Collection.class);
    }

    private Map<String, Object> getValuesAsMap() {
        return getInHeaderValue(exchange, RedisConstants.VALUES, new HashMap<String, Object>().getClass());
    }

    private String getKey() {
        return getInHeaderValue(exchange, RedisConstants.KEY, String.class);
    }

    public Collection<String> getKeys() {
        return getInHeaderValue(exchange, RedisConstants.KEYS, Collection.class);
    }

    private Object getValue() {
        return getInHeaderValue(exchange, RedisConstants.VALUE, Object.class);
    }

    private String getStringValue() {
        return getInHeaderValue(exchange, RedisConstants.VALUE, String.class);
    }

    private Long getLongValue() {
        return getInHeaderValue(exchange, RedisConstants.VALUE, Long.class);
    }

    private Boolean getBooleanValue() {
        return getInHeaderValue(exchange, RedisConstants.VALUE, Boolean.class);
    }

    private String getField() {
        return getInHeaderValue(exchange, RedisConstants.FIELD, String.class);
    }

    public Long getTimestamp() {
        return getInHeaderValue(exchange, RedisConstants.TIMESTAMP, Long.class);
    }

    public String getPattern() {
        return getInHeaderValue(exchange, RedisConstants.PATTERN, String.class);
    }

    public Integer getDb() {
        return getInHeaderValue(exchange, RedisConstants.DB, Integer.class);
    }

    public Double getScore() {
        return getInHeaderValue(exchange, RedisConstants.SCORE, Double.class);
    }

    public Double getMin() {
        return getInHeaderValue(exchange, RedisConstants.MIN, Double.class);
    }

    public Double getMax() {
        return getInHeaderValue(exchange, RedisConstants.MAX, Double.class);
    }

    public Double getIncrement() {
        return getInHeaderValue(exchange, RedisConstants.INCREMENT, Double.class);
    }

    public Boolean getWithScore() {
        return getInHeaderValue(exchange, RedisConstants.WITHSCORE, Boolean.class);
    }
}
