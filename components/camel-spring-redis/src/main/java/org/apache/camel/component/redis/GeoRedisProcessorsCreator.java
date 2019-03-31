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

public final class GeoRedisProcessorsCreator extends AbstractRedisProcessorCreator {

    Map<Command, Processor> getProcessors(RedisClient redisClient, ExchangeConverter exchangeConverter) {
        bind(Command.GEOADD, wrap(exchange -> redisClient.geoadd(exchangeConverter.getKey(exchange), exchangeConverter.getLongitude(exchange),
                                                                 exchangeConverter.getLatitude(exchange), exchangeConverter.getValue(exchange))));
        bind(Command.GEODIST, wrap(exchange -> redisClient.geodist(exchangeConverter.getKey(exchange), exchangeConverter.getValuesAsCollection(exchange).toArray()[0],
                                                                   exchangeConverter.getValuesAsCollection(exchange).toArray()[1])));
        bind(Command.GEOHASH, wrap(exchange -> redisClient.geohash(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange))));
        bind(Command.GEOPOS, wrap(exchange -> redisClient.geopos(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange))));
        bind(Command.GEORADIUS,
             wrap(exchange -> redisClient.georadius(exchangeConverter.getKey(exchange), exchangeConverter.getLongitude(exchange), exchangeConverter.getLatitude(exchange),
                                                    exchangeConverter.getRadius(exchange), exchangeConverter.getCount(exchange))));
        bind(Command.GEORADIUSBYMEMBER, wrap(exchange -> redisClient.georadius(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange),
                                                                               exchangeConverter.getRadius(exchange), exchangeConverter.getCount(exchange))));
        return result;
    }
}
