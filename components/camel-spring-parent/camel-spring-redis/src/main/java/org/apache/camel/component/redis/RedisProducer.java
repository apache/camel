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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.support.HeaderSelectorProducer;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;

public class RedisProducer extends HeaderSelectorProducer {

    private final ExchangeConverter exchangeConverter = new ExchangeConverter();
    private final RedisClient client;

    public RedisProducer(Endpoint endpoint,
                         String header,
                         String defaultHeaderValue,
                         RedisClient redisClient) {
        super(endpoint, header, defaultHeaderValue);
        this.client = redisClient;
    }

    @InvokeOnHeader("ECHO")
    public String invokeEcho(Exchange exchange) {
        return client.echo(exchangeConverter.getStringValue(exchange));
    }

    @InvokeOnHeader("PING")
    public String invokePing(Exchange exchange) {
        return client.ping();
    }

    @InvokeOnHeader("QUIT")
    public void invokeQuit(Exchange exchange) {
        client.quit();
    }

    @InvokeOnHeader("HDEL")
    public void invokeHdel(Exchange exchange) {
        client.hdel(exchangeConverter.getKey(exchange), exchangeConverter.getField(exchange));
    }

    @InvokeOnHeader("HEXISTS")
    public Boolean invokeHexists(Exchange exchange) {
        return client.hexists(exchangeConverter.getKey(exchange), exchangeConverter.getField(exchange));
    }

    @InvokeOnHeader("HGET")
    public Object invokeHget(Exchange exchange) {
        return client.hget(exchangeConverter.getKey(exchange), exchangeConverter.getField(exchange));
    }

    @InvokeOnHeader("HGETALL")
    public Map<String, Object> invokeHgetAll(Exchange exchange) {
        return client.hgetAll(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("HINCRBY")
    public Long invokeHincrBy(Exchange exchange) {
        return client.hincrBy(exchangeConverter.getKey(exchange), exchangeConverter.getField(exchange),
                exchangeConverter.getValueAsLong(exchange));
    }

    @InvokeOnHeader("HKEYS")
    public Set<String> invokeHkeys(Exchange exchange) {
        return client.hkeys(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("HLEN")
    public Long invokeHlen(Exchange exchange) {
        return client.hlen(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("HMGET")
    public Collection<Object> invokeHmget(Exchange exchange) {
        return client.hmget(exchangeConverter.getKey(exchange), exchangeConverter.getFields(exchange));
    }

    @InvokeOnHeader("HMSET")
    public void invokeHmset(Exchange exchange) {
        client.hmset(exchangeConverter.getKey(exchange), exchangeConverter.getValuesAsMap(exchange));
    }

    @InvokeOnHeader("HSET")
    public void invokeHset(Exchange exchange) {
        client.hset(exchangeConverter.getKey(exchange), exchangeConverter.getField(exchange),
                exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("HSETNX")
    public Boolean invokeHsetnx(Exchange exchange) {
        return client.hsetnx(exchangeConverter.getKey(exchange), exchangeConverter.getField(exchange),
                exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("HVALS")
    public Collection<Object> invokeHvals(Exchange exchange) {
        return client.hvals(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("DEL")
    public void invokeDel(Exchange exchange) {
        client.del(exchangeConverter.getKeys(exchange));
    }

    @InvokeOnHeader("EXISTS")
    public Boolean invokeExists(Exchange exchange) {
        return client.exists(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("EXPIRE")
    public Boolean invokeExpire(Exchange exchange) {
        return client.expire(exchangeConverter.getKey(exchange), exchangeConverter.getTimeout(exchange));
    }

    @InvokeOnHeader("EXPIREAT")
    public Boolean invokeExpireat(Exchange exchange) {
        return client.expireat(exchangeConverter.getKey(exchange), exchangeConverter.getTimestamp(exchange));
    }

    @InvokeOnHeader("KEYS")
    public Collection<String> invokeKeys(Exchange exchange) {
        return client.keys(exchangeConverter.getPattern(exchange));
    }

    @InvokeOnHeader("MOVE")
    public Boolean invokeMove(Exchange exchange) {
        return client.move(exchangeConverter.getKey(exchange), exchangeConverter.getDb(exchange));
    }

    @InvokeOnHeader("PERSIST")
    public Boolean invokePersist(Exchange exchange) {
        return client.persist(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("PEXPIRE")
    public Boolean invokePexpire(Exchange exchange) {
        return client.pexpire(exchangeConverter.getKey(exchange), exchangeConverter.getTimeout(exchange));
    }

    @InvokeOnHeader("PEXPIREAT")
    public Boolean invokePexpireat(Exchange exchange) {
        return client.pexpireat(exchangeConverter.getKey(exchange), exchangeConverter.getTimestamp(exchange));
    }

    @InvokeOnHeader("RANDOMKEY")
    public String invokeRandomkey(Exchange exchange) {
        return client.randomkey();
    }

    @InvokeOnHeader("RENAME")
    public void invokeRename(Exchange exchange) {
        client.rename(exchangeConverter.getKey(exchange), exchangeConverter.getStringValue(exchange));
    }

    @InvokeOnHeader("RENAMENX")
    public Boolean invokeRenamenx(Exchange exchange) {
        return client.renamenx(exchangeConverter.getKey(exchange), exchangeConverter.getStringValue(exchange));
    }

    @InvokeOnHeader("SORT")
    public List<Object> invokeSort(Exchange exchange) {
        return client.sort(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("TTL")
    public Long invokeTtl(Exchange exchange) {
        return client.ttl(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("TYPE")
    public String invokeType(Exchange exchange) {
        return client.type(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("BLPOP")
    public Object invokeBlpop(Exchange exchange) {
        return client.blpop(exchangeConverter.getKey(exchange), exchangeConverter.getTimeout(exchange));
    }

    @InvokeOnHeader("BRPOP")
    public Object invokeBrpop(Exchange exchange) {
        return client.brpop(exchangeConverter.getKey(exchange), exchangeConverter.getTimeout(exchange));
    }

    @InvokeOnHeader("BRPOPLPUSH")
    public Object invokeBrpoplpush(Exchange exchange) {
        return client.brpoplpush(exchangeConverter.getKey(exchange), exchangeConverter.getDestination(exchange),
                exchangeConverter.getTimeout(exchange));
    }

    @InvokeOnHeader("LINDEX")
    public Object invokeLindex(Exchange exchange) {
        return client.lindex(exchangeConverter.getKey(exchange), exchangeConverter.getIndex(exchange));
    }

    @InvokeOnHeader("LINSERT")
    public Object invokeLinsert(Exchange exchange) {
        return client.linsert(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange),
                exchangeConverter.getPivot(exchange),
                exchangeConverter.getPosition(exchange));
    }

    @InvokeOnHeader("LLEN")
    public Long invokeLlen(Exchange exchange) {
        return client.llen(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("LPOP")
    public Object invokeLpop(Exchange exchange) {
        return client.lpop(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("LPUSH")
    public Long invokeLpush(Exchange exchange) {
        return client.lpush(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("LPUSHX")
    public Long invokeLpushx(Exchange exchange) {
        return client.lpushx(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("LRANGE")
    public List<Object> invokeLrange(Exchange exchange) {
        return client.lrange(exchangeConverter.getKey(exchange), exchangeConverter.getStart(exchange),
                exchangeConverter.getEnd(exchange));
    }

    @InvokeOnHeader("LREM")
    public Long invokeLrem(Exchange exchange) {
        return client.lrem(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange),
                exchangeConverter.getCount(exchange));
    }

    @InvokeOnHeader("LSET")
    public void invokeLset(Exchange exchange) {
        client.lset(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange),
                exchangeConverter.getIndex(exchange));
    }

    @InvokeOnHeader("LTRIM")
    public void invokeLtrim(Exchange exchange) {
        client.ltrim(exchangeConverter.getKey(exchange), exchangeConverter.getStart(exchange),
                exchangeConverter.getEnd(exchange));
    }

    @InvokeOnHeader("RPOP")
    public Object invokeRpop(Exchange exchange) {
        return client.rpop(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("RPOPLPUSH")
    public Object invokeRpoplpush(Exchange exchange) {
        return client.rpoplpush(exchangeConverter.getKey(exchange), exchangeConverter.getDestination(exchange));
    }

    @InvokeOnHeader("RPUSH")
    public Long invokeRpush(Exchange exchange) {
        return client.rpush(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("RPUSHX")
    public Long invokeRpushx(Exchange exchange) {
        return client.rpushx(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("PUBLISH")
    public void invokePublish(Exchange exchange) {
        client.publish(exchangeConverter.getChannel(exchange), exchangeConverter.getMessage(exchange));
    }

    @InvokeOnHeader("SADD")
    public Long invokeSadd(Exchange exchange) {
        return client.sadd(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("SCARD")
    public Long invokeScard(Exchange exchange) {
        return client.scard(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("SDIFF")
    public Set<Object> invokeSdiff(Exchange exchange) {
        return client.sdiff(exchangeConverter.getKey(exchange), exchangeConverter.getKeys(exchange));
    }

    @InvokeOnHeader("SDIFFSTORE")
    public void invokeSdiffstore(Exchange exchange) {
        client.sdiffstore(exchangeConverter.getKey(exchange), exchangeConverter.getKeys(exchange),
                exchangeConverter.getDestination(exchange));
    }

    @InvokeOnHeader("SINTER")
    public Set<Object> invokeSinter(Exchange exchange) {
        return client.sinter(exchangeConverter.getKey(exchange), exchangeConverter.getKeys(exchange));
    }

    @InvokeOnHeader("SINTERSTORE")
    public void invokeSinterstore(Exchange exchange) {
        client.sinterstore(exchangeConverter.getKey(exchange), exchangeConverter.getKeys(exchange),
                exchangeConverter.getDestination(exchange));
    }

    @InvokeOnHeader("SISMEMBER")
    public Boolean invokeSismember(Exchange exchange) {
        return client.sismember(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("SMEMBERS")
    public Set<Object> invokeSmembers(Exchange exchange) {
        return client.smembers(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("SMOVE")
    public Boolean invokeSmove(Exchange exchange) {
        return client.smove(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange),
                exchangeConverter.getDestination(exchange));
    }

    @InvokeOnHeader("SPOP")
    public Object invokeSpop(Exchange exchange) {
        return client.spop(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("SRANDMEMBER")
    public Object invokeSrandmember(Exchange exchange) {
        return client.srandmember(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("SREM")
    public Long invokeSrem(Exchange exchange) {
        return client.srem(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("SUNION")
    public Set<Object> invokeSunion(Exchange exchange) {
        return client.sunion(exchangeConverter.getKey(exchange), exchangeConverter.getKeys(exchange));
    }

    @InvokeOnHeader("SUNIONSTORE")
    public void invokeSunionstore(Exchange exchange) {
        client.sunionstore(exchangeConverter.getKey(exchange), exchangeConverter.getKeys(exchange),
                exchangeConverter.getDestination(exchange));
    }

    @InvokeOnHeader("ZADD")
    public Boolean invokeZadd(Exchange exchange) {
        return client.zadd(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange),
                exchangeConverter.getScore(exchange));
    }

    @InvokeOnHeader("ZCARD")
    public Long invokeZcard(Exchange exchange) {
        return client.zcard(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("ZCOUNT")
    public Long invokeZcount(Exchange exchange) {
        return client.zcount(exchangeConverter.getKey(exchange), exchangeConverter.getMin(exchange),
                exchangeConverter.getMax(exchange));
    }

    @InvokeOnHeader("ZINCRBY")
    public Double invokeZincrby(Exchange exchange) {
        return client.zincrby(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange),
                exchangeConverter.getIncrement(exchange));
    }

    @InvokeOnHeader("ZINTERSTORE")
    public void invokeZinterstore(Exchange exchange) {
        client.zinterstore(exchangeConverter.getKey(exchange), exchangeConverter.getKeys(exchange),
                exchangeConverter.getDestination(exchange));
    }

    @InvokeOnHeader("ZRANGE")
    public Object invokeZrange(Exchange exchange) {
        return client.zrange(exchangeConverter.getKey(exchange), exchangeConverter.getStart(exchange),
                exchangeConverter.getEnd(exchange),
                exchangeConverter.getWithScore(exchange));
    }

    @InvokeOnHeader("ZRANGEBYSCORE")
    public Set<Object> invokeZrangebyscore(Exchange exchange) {
        return client.zrangebyscore(exchangeConverter.getKey(exchange),
                exchangeConverter.getMin(exchange),
                exchangeConverter.getMax(exchange));
    }

    @InvokeOnHeader("ZRANK")
    public Long invokeZrank(Exchange exchange) {
        return client.zrank(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("ZREM")
    public Long invokeZrem(Exchange exchange) {
        return client.zrem(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("ZREMRANGEBYRANK")
    public void invokeZremrangebyrank(Exchange exchange) {
        client.zremrangebyrank(exchangeConverter.getKey(exchange),
                exchangeConverter.getStart(exchange), exchangeConverter.getEnd(exchange));
    }

    @InvokeOnHeader("ZREMRANGEBYSCORE")
    public void invokeZremrangebyscore(Exchange exchange) {
        client.zremrangebyscore(exchangeConverter.getKey(exchange),
                exchangeConverter.getStart(exchange), exchangeConverter.getEnd(exchange));
    }

    @InvokeOnHeader("ZREVRANGE")
    public Object invokeZrevrange(Exchange exchange) {
        return client.zrevrange(exchangeConverter.getKey(exchange),
                exchangeConverter.getStart(exchange), exchangeConverter.getEnd(exchange),
                exchangeConverter.getWithScore(exchange));
    }

    @InvokeOnHeader("ZREVRANGEBYSCORE")
    public Set<Object> invokeZrevrangebyscore(Exchange exchange) {
        return client.zrevrangebyscore(exchangeConverter.getKey(exchange),
                exchangeConverter.getMin(exchange), exchangeConverter.getMax(exchange));
    }

    @InvokeOnHeader("ZREVRANK")
    public Long invokeZrevrank(Exchange exchange) {
        return client.zrevrank(exchangeConverter.getKey(exchange),
                exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("ZUNIONSTORE")
    public void invokeZunionstore(Exchange exchange) {
        client.zunionstore(exchangeConverter.getKey(exchange),
                exchangeConverter.getKeys(exchange), exchangeConverter.getDestination(exchange));
    }

    @InvokeOnHeader("APPEND")
    public Integer invokeAppend(Exchange exchange) {
        return client.append(exchangeConverter.getKey(exchange), exchangeConverter.getStringValue(exchange));
    }

    @InvokeOnHeader("DECR")
    public Long invokeDecr(Exchange exchange) {
        return client.decr(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("DECRBY")
    public Long invokeDecrby(Exchange exchange) {
        return client.decrby(exchangeConverter.getKey(exchange), exchangeConverter.getLongValue(exchange));
    }

    @InvokeOnHeader("GET")
    public Object invokeGet(Exchange exchange) {
        return client.get(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("GETBIT")
    public Boolean invokeGetbit(Exchange exchange) {
        return client.getbit(exchangeConverter.getKey(exchange), exchangeConverter.getOffset(exchange));
    }

    @InvokeOnHeader("GETRANGE")
    public String invokeGetrange(Exchange exchange) {
        return client.getrange(exchangeConverter.getKey(exchange), exchangeConverter.getStart(exchange),
                exchangeConverter.getEnd(exchange));
    }

    @InvokeOnHeader("SETRANGE")
    public void invokeSetrange(Exchange exchange) {
        client.setex(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange),
                exchangeConverter.getOffset(exchange));
    }

    @InvokeOnHeader("GETSET")
    public Object invokeGetset(Exchange exchange) {
        return client.getset(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("INCR")
    public Long invokeIncr(Exchange exchange) {
        return client.incr(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("INCRBY")
    public Long invokeIncrby(Exchange exchange) {
        return client.incrby(exchangeConverter.getKey(exchange), exchangeConverter.getLongValue(exchange));
    }

    @InvokeOnHeader("MGET")
    public List<Object> invokeMget(Exchange exchange) {
        return client.mget(exchangeConverter.getFields(exchange));
    }

    @InvokeOnHeader("MSET")
    public void invokeMset(Exchange exchange) {
        client.mset(exchangeConverter.getValuesAsMap(exchange));
    }

    @InvokeOnHeader("MSETNX")
    public void invokeMsetnx(Exchange exchange) {
        client.msetnx(exchangeConverter.getValuesAsMap(exchange));
    }

    @InvokeOnHeader("SET")
    public void invokeSet(Exchange exchange) {
        client.set(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("SETBIT")
    public void invokeSetbit(Exchange exchange) {
        client.setbit(exchangeConverter.getKey(exchange), exchangeConverter.getOffset(exchange),
                exchangeConverter.getBooleanValue(exchange));
    }

    @InvokeOnHeader("SETEX")
    public void invokeSetex(Exchange exchange) {
        client.setex(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange),
                exchangeConverter.getTimeout(exchange), TimeUnit.SECONDS);
    }

    @InvokeOnHeader("SETNX")
    public Boolean invokeSetnx(Exchange exchange) {
        return client.setnx(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("STRLEN")
    public Long invokeStrlen(Exchange exchange) {
        return client.strlen(exchangeConverter.getKey(exchange));
    }

    @InvokeOnHeader("DISCARD")
    public void invokeDiscard(Exchange exchange) {
        client.discard();
    }

    @InvokeOnHeader("EXEC")
    public void invokeExec(Exchange exchange) {
        client.exec();
    }

    @InvokeOnHeader("MULTI")
    public void invokeMulti(Exchange exchange) {
        client.multi();
    }

    @InvokeOnHeader("WATCH")
    public void invokeWatch(Exchange exchange) {
        client.watch(exchangeConverter.getKeys(exchange));
    }

    @InvokeOnHeader("UNWATCH")
    public void invokeUnwatch(Exchange exchange) {
        client.unwatch();
    }

    @InvokeOnHeader("GEOADD")
    public Long invokeGeoadd(Exchange exchange) {
        return client.geoadd(exchangeConverter.getKey(exchange), exchangeConverter.getLongitude(exchange),
                exchangeConverter.getLatitude(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("GEODIST")
    public Distance invokeGeodist(Exchange exchange) {
        return client.geodist(exchangeConverter.getKey(exchange),
                exchangeConverter.getValuesAsCollection(exchange).toArray()[0],
                exchangeConverter.getValuesAsCollection(exchange).toArray()[1]);
    }

    @InvokeOnHeader("GEOHASH")
    public List<String> invokeGeohash(Exchange exchange) {
        return client.geohash(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("GEOPOS")
    public List<Point> invokeGeopos(Exchange exchange) {
        return client.geopos(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange));
    }

    @InvokeOnHeader("GEORADIUS")
    public GeoResults<RedisGeoCommands.GeoLocation<Object>> invokeGeoradius(Exchange exchange) {
        return client.georadius(exchangeConverter.getKey(exchange), exchangeConverter.getLongitude(exchange),
                exchangeConverter.getLatitude(exchange),
                exchangeConverter.getRadius(exchange), exchangeConverter.getCount(exchange));
    }

    @InvokeOnHeader("GEORADIUSBYMEMBER")
    public GeoResults<RedisGeoCommands.GeoLocation<Object>> invokeGeoradiusbymember(Exchange exchange) {
        return client.georadius(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange),
                exchangeConverter.getRadius(exchange), exchangeConverter.getCount(exchange));
    }

}
