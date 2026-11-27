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

import org.apache.camel.spi.Metadata;

public interface RedisConstants {
    @Metadata(label = "producer",
              description = "The name of the Redis command that the producer runs, such as GET or HSET, in any case."
                            + " Replaces the command option for this message.",
              javaType = "String")
    String COMMAND = "CamelRedis.Command";
    @Metadata(label = "producer",
              description = "The key that the command works on, used by most commands, such as GET, SET, HSET, LPUSH, SADD"
                            + " and ZADD. For SDIFF, SINTER, SUNION, their STORE forms, ZINTERSTORE and ZUNIONSTORE, the first"
                            + " key, which is combined with the keys of the CamelRedis.Keys header.",
              javaType = "String")
    String KEY = "CamelRedis.Key";
    @Metadata(label = "producer",
              description = "The keys to delete for DEL and to watch for WATCH, and for SDIFF, SINTER, SUNION, their STORE"
                            + " forms, ZINTERSTORE and ZUNIONSTORE the keys that are combined with the key of the"
                            + " CamelRedis.Key header. A collection of keys, or a String with one key or several keys"
                            + " separated by comma.",
              javaType = "Collection<String> or String")
    String KEYS = "CamelRedis.Keys";
    @Metadata(label = "producer",
              description = "The field of the hash for HDEL, HEXISTS, HGET, HINCRBY, HSET and HSETNX.",
              javaType = "String")
    String FIELD = "CamelRedis.Field";
    @Metadata(label = "producer",
              description = "The fields of the hash to read for HMGET, and the keys to read for MGET.",
              javaType = "Collection<String>")
    String FIELDS = "CamelRedis.Fields";
    @Metadata(label = "producer",
              description = "The value for the commands that store, add or look up one value or member, such as SET, SETNX,"
                            + " GETSET, HSET, LPUSH, SADD, SISMEMBER, ZADD and GEOADD. It is a Long for INCRBY, DECRBY and"
                            + " HINCRBY, a Boolean for SETBIT, the new name of the key for RENAME and RENAMENX, and text for"
                            + " APPEND and ECHO. The serializer of the RedisTemplate writes it, so with the default JDK"
                            + " serializer it must be Serializable.",
              javaType = "Object")
    String VALUE = "CamelRedis.Value";
    @Metadata(label = "producer",
              description = "A Map of keys to values for MSET and MSETNX, and of fields to values for HMSET. For GEODIST, a"
                            + " Collection whose first two elements are the members to measure the distance between.",
              javaType = "Map<String, Object> or Collection<Object>")
    String VALUES = "CamelRedis.Values";
    @Metadata(label = "producer",
              description = "The index of the first element for LRANGE, LTRIM, ZRANGE, ZREVRANGE and ZREMRANGEBYRANK, the"
                            + " offset of the first byte for GETRANGE, and the minimum score for ZREMRANGEBYSCORE when"
                            + " CamelRedis.Min is not set. Indexes start at 0, and a negative index counts from the end.",
              javaType = "Long")
    String START = "CamelRedis.Start";
    @Metadata(label = "producer",
              description = "The index of the last element, inclusive, for LRANGE, LTRIM, ZRANGE, ZREVRANGE and"
                            + " ZREMRANGEBYRANK, the offset of the last byte for GETRANGE, and the maximum score for"
                            + " ZREMRANGEBYSCORE when CamelRedis.Max is not set. Indexes start at 0, and a negative index counts from the end, so -1 is the"
                            + " last element.",
              javaType = "Long")
    String END = "CamelRedis.End";
    @Metadata(label = "producer",
              description = "The time to live of the key in seconds for EXPIRE and SETEX, and in milliseconds for PEXPIRE."
                            + " For BLPOP, BRPOP and BRPOPLPUSH, how many seconds to wait for an element while the list is"
                            + " empty.",
              javaType = "Long")
    String TIMEOUT = "CamelRedis.Timeout";
    @Metadata(label = "producer",
              description = "The offset of the bit for GETBIT and SETBIT, and the offset in bytes where SETRANGE writes the"
                            + " value.",
              javaType = "Long")
    String OFFSET = "CamelRedis.Offset";
    @Metadata(label = "producer",
              description = "The key that receives the result for RPOPLPUSH, BRPOPLPUSH, SMOVE, SDIFFSTORE, SINTERSTORE,"
                            + " SUNIONSTORE, ZINTERSTORE and ZUNIONSTORE.",
              javaType = "String")
    String DESTINATION = "CamelRedis.Destination";
    @Metadata(description = "The channel that PUBLISH posts the message to. The consumer sets it to the name of the channel"
                            + " that the message was received on.",
              javaType = "String")
    String CHANNEL = "CamelRedis.Channel";
    @Metadata(label = "producer",
              description = "The message that PUBLISH posts to the channel of the CamelRedis.Channel header. The serializer"
                            + " of the RedisTemplate writes it, so with the default JDK serializer it must be Serializable.",
              javaType = "Object")
    String MESSAGE = "CamelRedis.Message";
    @Metadata(label = "producer",
              description = "The index of the element of the list for LINDEX and LSET. Indexes start at 0, and a negative"
                            + " index counts from the end, so -1 is the last element.",
              javaType = "Long")
    String INDEX = "CamelRedis.Index";
    @Metadata(label = "producer",
              description = "Where LINSERT inserts the value: BEFORE or AFTER the pivot, in upper case.",
              javaType = "String")
    String POSITION = "CamelRedis.Position";
    @Metadata(label = "producer",
              description = "The element of the list that LINSERT inserts the value before or after.",
              javaType = "String")
    String PIVOT = "CamelRedis.Pivot";
    @Metadata(label = "producer",
              description = "For LREM, how many elements equal to the value to remove: a positive count removes from the"
                            + " head, a negative count from the tail, and 0 removes all of them. For GEORADIUS and"
                            + " GEORADIUSBYMEMBER, the maximum number of members to return; if not set, all of them are"
                            + " returned.",
              javaType = "Long")
    String COUNT = "CamelRedis.Count";
    @Metadata(label = "producer",
              description = "The time when the key expires, in seconds since the epoch for EXPIREAT and in milliseconds"
                            + " since the epoch for PEXPIREAT.",
              javaType = "Long")
    String TIMESTAMP = "CamelRedis.Timestamp";
    @Metadata(description = "The glob-style pattern of the keys that KEYS returns, such as user:*. With the PSUBSCRIBE"
                            + " command, the consumer sets it, as a byte array, to the pattern of the channels option that the"
                            + " channel of the message matched.",
              javaType = "byte[] or String")
    String PATTERN = "CamelRedis.Pattern";
    @Metadata(label = "producer",
              description = "The index of the database that MOVE moves the key to.",
              javaType = "Integer")
    String DB = "CamelRedis.Db";
    @Metadata(label = "producer",
              description = "The score of the member that ZADD adds.",
              javaType = "Double")
    String SCORE = "CamelRedis.Score";
    @Metadata(label = "producer",
              description = "The minimum score, inclusive, for ZCOUNT, ZRANGEBYSCORE, ZREVRANGEBYSCORE and"
                            + " ZREMRANGEBYSCORE.",
              javaType = "Double")
    String MIN = "CamelRedis.Min";
    @Metadata(label = "producer",
              description = "The maximum score, inclusive, for ZCOUNT, ZRANGEBYSCORE, ZREVRANGEBYSCORE and"
                            + " ZREMRANGEBYSCORE.",
              javaType = "Double")
    String MAX = "CamelRedis.Max";
    @Metadata(label = "producer",
              description = "The amount that ZINCRBY adds to the score of the member.",
              javaType = "Double")
    String INCREMENT = "CamelRedis.Increment";
    @Metadata(label = "producer",
              description = "Whether ZRANGE and ZREVRANGE return the members together with their scores, as a Set of"
                            + " ZSetOperations.TypedTuple. If not set or false, they return the members only.",
              javaType = "Boolean")
    String WITHSCORE = "CamelRedis.WithScore";
    @Metadata(label = "producer",
              description = "The latitude of the member that GEOADD adds, and of the center of the area that GEORADIUS"
                            + " searches.",
              javaType = "Double")
    String LATITUDE = "CamelRedis.Latitude";
    @Metadata(label = "producer",
              description = "The longitude of the member that GEOADD adds, and of the center of the area that GEORADIUS"
                            + " searches.",
              javaType = "Double")
    String LONGITUDE = "CamelRedis.Longitude";
    @Metadata(label = "producer",
              description = "The radius in meters of the area that GEORADIUS and GEORADIUSBYMEMBER search.",
              javaType = "Double")
    String RADIUS = "CamelRedis.Radius";
}
