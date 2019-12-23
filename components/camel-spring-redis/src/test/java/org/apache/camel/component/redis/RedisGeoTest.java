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

import org.apache.camel.BindToRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RedisGeoTest extends RedisTestSupport {

    @Mock
    @BindToRegistry("redisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private GeoOperations<String, String> geoOperations;

    @Before
    public void setupTests() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
    }

    @Test
    public void shouldExecuteGEOADD() throws Exception {
        sendHeaders(RedisConstants.COMMAND, Command.GEOADD, RedisConstants.KEY, "Sicily", RedisConstants.LONGITUDE, 13.361389, RedisConstants.LATITUDE, 38.115556,
                    RedisConstants.VALUE, "Palermo");
        verify(redisTemplate).opsForGeo();
        verify(geoOperations).add("Sicily", new Point(13.361389, 38.115556), "Palermo");
    }

    @Test
    public void shouldExecuteGEODIST() throws Exception {
        Object[] members = new String[] {"Palermo", "Catania"};
        sendHeaders(RedisConstants.COMMAND, Command.GEODIST, RedisConstants.KEY, "Sicily", RedisConstants.VALUES, members);
        verify(redisTemplate).opsForGeo();
        verify(geoOperations).distance("Sicily", "Palermo", "Catania");
    }

    @Test
    public void shouldExecuteGEOHASH() throws Exception {
        sendHeaders(RedisConstants.COMMAND, Command.GEOHASH, RedisConstants.KEY, "Sicily", RedisConstants.VALUE, "Palermo");
        verify(redisTemplate).opsForGeo();
        verify(geoOperations).hash("Sicily", "Palermo");
    }

    @Test
    public void shouldExecuteGEOPOS() throws Exception {
        sendHeaders(RedisConstants.COMMAND, Command.GEOPOS, RedisConstants.KEY, "Sicily", RedisConstants.VALUE, "Palermo");
        verify(redisTemplate).opsForGeo();
        verify(geoOperations).position("Sicily", "Palermo");
    }

    @Test
    public void shouldExecuteGEORADIUS() throws Exception {
        sendHeaders(RedisConstants.COMMAND, Command.GEORADIUS, RedisConstants.KEY, "Sicily", RedisConstants.LONGITUDE, 13.361389, RedisConstants.LATITUDE, 38.115556,
                    RedisConstants.RADIUS, 200000, RedisConstants.COUNT, 10);
        verify(redisTemplate).opsForGeo();
        verify(geoOperations).radius(eq("Sicily"), eq(new Circle(new Point(13.361389, 38.115556), 200000)), any(GeoRadiusCommandArgs.class));
    }

    @Test
    public void shouldExecuteGEORADIUSBYMEMBER() throws Exception {
        sendHeaders(RedisConstants.COMMAND, Command.GEORADIUSBYMEMBER, RedisConstants.KEY, "Sicily", RedisConstants.VALUE, "Palermo", RedisConstants.RADIUS, 200000);
        verify(redisTemplate).opsForGeo();
        verify(geoOperations).radius(eq("Sicily"), eq("Palermo"), eq(new Distance(200000)), any(GeoRadiusCommandArgs.class));
    }
}
