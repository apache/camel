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

public enum Command {
    PING, SET, GET, QUIT, EXISTS, DEL, TYPE, FLUSHDB, KEYS, RANDOMKEY,
    RENAME, RENAMENX, RENAMEX, DBSIZE, EXPIRE, EXPIREAT, TTL, SELECT,
    MOVE, FLUSHALL, GETSET, MGET, SETNX, SETEX, MSET, MSETNX, DECRBY,
    DECR, INCRBY, INCR, APPEND, SUBSTR, HSET, HGET, HSETNX, HMSET, HMGET,
    HINCRBY, HEXISTS, HDEL, HLEN, HKEYS, HVALS, HGETALL, RPUSH, LPUSH,
    LLEN, LRANGE, LTRIM, LINDEX, LSET, LREM, LPOP, RPOP, RPOPLPUSH, SADD,
    SMEMBERS, SREM, SPOP, SMOVE, SCARD, SISMEMBER, SINTER, SINTERSTORE,
    SUNION, SUNIONSTORE, SDIFF, SDIFFSTORE, SRANDMEMBER, ZADD, ZRANGE,
    ZREM, ZINCRBY, ZRANK, ZREVRANK, ZREVRANGE, ZCARD, ZSCORE, MULTI, DISCARD,
    EXEC, WATCH, UNWATCH, SORT, BLPOP, BRPOP, AUTH, SUBSCRIBE, PUBLISH,
    UNSUBSCRIBE, PSUBSCRIBE, PUNSUBSCRIBE, ZCOUNT, ZRANGEBYSCORE, ZREVRANGEBYSCORE,
    ZREMRANGEBYRANK, ZREMRANGEBYSCORE, ZUNIONSTORE, ZINTERSTORE, SAVE, BGSAVE,
    BGREWRITEAOF, LASTSAVE, SHUTDOWN, INFO, MONITOR, SLAVEOF, CONFIG, STRLEN,
    SYNC, LPUSHX, PERSIST, RPUSHX, ECHO, LINSERT, DEBUG, BRPOPLPUSH, SETBIT,
    GETBIT, SETRANGE, GETRANGE, PEXPIRE, PEXPIREAT,
    GEOADD, GEODIST, GEOHASH, GEOPOS, GEORADIUS, GEORADIUSBYMEMBER
}
