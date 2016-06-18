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
package org.apache.camel.component.influxdb.converters;

import org.apache.camel.Converter;
import org.apache.camel.component.influxdb.InfluxDbConstants;
import org.influxdb.dto.Point;

import java.math.BigInteger;
import java.util.Map;

/**
 * Created by jose on 18/06/16.
 */
@Converter
public final class CamelInfluxDbConverters {

    private CamelInfluxDbConverters() {

    }

    @Converter
    public static Point fromMapToPoint(Map<?, ?> map) {
        Point p = null;
        Object measurenmentName = map.get(InfluxDbConstants.MEASUREMENT_NAME);

        String measurenmentNameString = (String) measurenmentName;
        Point.Builder pointBuilder = Point.measurement(measurenmentNameString);

        for (Object entry :map.keySet()) {
            String key = entry.toString();
            Object value = map.get(entry);

            if (value instanceof Number) {
                if (value instanceof Byte) {
                    pointBuilder.addField(measurenmentNameString, ((Byte) value).doubleValue());
                }
                if (value instanceof Short) {
                    pointBuilder.addField(measurenmentNameString, ((Short) value).doubleValue());
                }
                if (value instanceof Integer) {
                    pointBuilder.addField(measurenmentNameString,  ((Integer) value).doubleValue());
                }
                if (value instanceof Long) {
                    pointBuilder.addField(measurenmentNameString, ((Long) value).doubleValue());
                }
                if (value instanceof Double) {
                    pointBuilder.addField(measurenmentNameString, ((BigInteger) value).doubleValue());
                }

            }

        }


        return p;

    }
}
