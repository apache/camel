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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * List of influxdb2 Measurement
 * <p>
 * {@link Measurement}.
 */
public final class Measurements {
    private List<Measurement> measurements = new ArrayList<>();

    private Measurements() {
    }

    private Measurements(List<Measurement> measurements) {
        this.measurements = measurements;
    }

    public static Measurements create() {
        return new Measurements();
    }

    public static Measurements create(List<Measurement> measurements) {
        return new Measurements(measurements);
    }

    public static Measurements create(Measurement measurement) {
        return new Measurements().addMeasurement(measurement);
    }

    public static Measurements create(Object measurement) {
        return create(Measurement.fromObject(measurement));
    }

    /**
     * get influxdb2 write measurements
     *
     * @return List<Measurement>
     */
    public List<Object> getInfluxMeasurements() {
        return measurements.stream().map(Measurement::getInfluxMeasurement).collect(Collectors.toList());
    }

    public Measurements setMeasurements(List<Measurement> measurements) {
        this.measurements = measurements;
        return this;
    }

    public Measurements addMeasurement(Measurement measurement) {
        this.measurements.add(measurement);
        return this;
    }

    @Override
    public String toString() {
        return "Measurements{" + "measurements=" + measurements + '}';
    }
}
