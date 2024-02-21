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
package org.apache.camel.component.influxdb2.converters;

import java.util.Map;

import com.influxdb.client.write.Point;
import org.apache.camel.Converter;
import org.apache.camel.component.influxdb2.CamelInfluxDb2Exception;
import org.apache.camel.component.influxdb2.InfluxDb2Constants;

/**
 * Called after InvalidPayloadException raised.
 */
@Converter(generateLoader = true)
public final class CamelInfluxDb2Converters {

    private CamelInfluxDb2Converters() {
    }

    @Converter
    public static Point fromMapToPoint(Map<String, Object> map) {
        Object measurementName = map.get(InfluxDb2Constants.MEASUREMENT);
        if (measurementName == null) {
            String format = String.format("Unable to find the header for the measurement in: %s", map.keySet().toString());
            throw new CamelInfluxDb2Exception(format);
        }

        String measurenmentNameString = measurementName.toString();
        Point point = Point.measurement(measurenmentNameString);
        map.remove(InfluxDb2Constants.MEASUREMENT);
        point.addFields(map);
        map.put(InfluxDb2Constants.MEASUREMENT, measurementName);

        return point;
    }
}
