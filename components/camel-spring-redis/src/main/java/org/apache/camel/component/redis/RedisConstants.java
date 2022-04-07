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
    @Metadata(label = "producer", description = "The command to perform.", javaType = "String")
    String COMMAND = "CamelRedis.Command";
    @Metadata(description = "The key.", javaType = "String")
    String KEY = "CamelRedis.Key";
    @Metadata(description = "The keys.", javaType = "Collection<String>")
    String KEYS = "CamelRedis.Keys";
    @Metadata(description = "The field.", javaType = "String")
    String FIELD = "CamelRedis.Field";
    @Metadata(description = "The fields.", javaType = "Collection<String>")
    String FIELDS = "CamelRedis.Fields";
    @Metadata(description = "The value.", javaType = "Object")
    String VALUE = "CamelRedis.Value";
    @Metadata(description = "The values.", javaType = "Map<String, Object> or Collection<Object>")
    String VALUES = "CamelRedis.Values";
    @Metadata(description = "Start", javaType = "Long")
    String START = "CamelRedis.Start";
    @Metadata(description = "End", javaType = "Long")
    String END = "CamelRedis.End";
    @Metadata(description = "The timeout.", javaType = "Long")
    String TIMEOUT = "CamelRedis.Timeout";
    @Metadata(description = "The offset.", javaType = "Long")
    String OFFSET = "CamelRedis.Offset";
    @Metadata(description = "The destination.", javaType = "String")
    String DESTINATION = "CamelRedis.Destination";
    @Metadata(description = "The channel.", javaType = "byte[] or String")
    String CHANNEL = "CamelRedis.Channel";
    @Metadata(description = "The message.", javaType = "Object")
    String MESSAGE = "CamelRedis.Message";
    @Metadata(description = "The index.", javaType = "Long")
    String INDEX = "CamelRedis.Index";
    @Metadata(description = "The position.", javaType = "String")
    String POSITION = "CamelRedis.Position";
    @Metadata(description = "The pivot.", javaType = "String")
    String PIVOT = "CamelRedis.Pivot";
    @Metadata(description = "Count", javaType = "Long")
    String COUNT = "CamelRedis.Count";
    @Metadata(description = "The timestamp.", javaType = "Long")
    String TIMESTAMP = "CamelRedis.Timestamp";
    @Metadata(description = "The pattern.", javaType = "byte[] or String")
    String PATTERN = "CamelRedis.Pattern";
    @Metadata(description = "The db.", javaType = "Integer")
    String DB = "CamelRedis.Db";
    @Metadata(description = "The score.", javaType = "Double")
    String SCORE = "CamelRedis.Score";
    @Metadata(description = "The min.", javaType = "Double")
    String MIN = "CamelRedis.Min";
    @Metadata(description = "The max.", javaType = "Double")
    String MAX = "CamelRedis.Max";
    @Metadata(description = "Increment.", javaType = "Double")
    String INCREMENT = "CamelRedis.Increment";
    @Metadata(description = "WithScore.", javaType = "Boolean")
    String WITHSCORE = "CamelRedis.WithScore";
    @Metadata(description = "Latitude.", javaType = "Double")
    String LATITUDE = "CamelRedis.Latitude";
    @Metadata(description = "Longitude.", javaType = "Double")
    String LONGITUDE = "CamelRedis.Longitude";
    @Metadata(description = "Radius.", javaType = "Double")
    String RADIUS = "CamelRedis.Radius";
}
