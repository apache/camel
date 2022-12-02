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
package org.apache.camel.component.influxdb2.data;

/**
 * InfluxDb write measurement.
 */
public final class Measurement {
    private Object measurement;

    private Measurement(Object measurement) {
        this.measurement = measurement;
    }

    public static Measurement fromObject(Object measurement) throws IllegalArgumentException {
        check(measurement);
        return new Measurement(measurement);
    }

    /**
     * get influxdb2 write measurement
     *
     * @return Object
     */
    public Object getInfluxMeasurement() {
        return measurement;
    }

    public static void check(Object measurement) {
        if (!measurement.getClass().isAnnotationPresent(com.influxdb.annotations.Measurement.class)) {
            throw new IllegalArgumentException(
                    "There is no " + com.influxdb.annotations.Measurement.class.getCanonicalName() + " annotation for "
                                               + measurement + "[" + measurement.getClass() + "]");
        }
    }

    @Override
    public String toString() {
        return "Measurement{" +
               "measurement=" + measurement +
               '}';
    }
}
