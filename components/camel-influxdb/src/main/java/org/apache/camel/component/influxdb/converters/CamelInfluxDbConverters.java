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

import java.util.Map;

import org.apache.camel.Converter;
import org.apache.camel.component.influxdb.CamelInfluxDbException;
import org.apache.camel.component.influxdb.InfluxDbConstants;
import org.influxdb.dto.Point;

@Converter
public final class CamelInfluxDbConverters {

    private CamelInfluxDbConverters() {

    }

    @Converter
    public static Point fromMapToPoint(Map<String, Object> map) {

        Object measurenmentName = map.get(InfluxDbConstants.MEASUREMENT_NAME);

        if (measurenmentName == null) {
            String format = String.format("Unable to find the header for the measurement in:" + map.keySet().toString());
            throw new CamelInfluxDbException(format);
        }


        String measurenmentNameString = measurenmentName.toString();
        Point.Builder pointBuilder = Point.measurement(measurenmentNameString);

        map.remove(InfluxDbConstants.MEASUREMENT_NAME);

        pointBuilder.fields(map);
        map.put(InfluxDbConstants.MEASUREMENT_NAME, measurenmentName);


        return pointBuilder.build();

    }


}
