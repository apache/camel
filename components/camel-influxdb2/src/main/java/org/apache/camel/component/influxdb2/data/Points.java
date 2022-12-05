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

import com.influxdb.client.write.Point;

/**
 * List of InfluxDb write point
 * <p>
 * {@link Point}
 */
public final class Points {
    private List<Point> points = new ArrayList<>();

    private Points() {

    }

    private Points(List<Point> points) {
        this.points = points;
    }

    public static Points create() {
        return new Points();
    }

    public static Points create(List<Point> points) {
        return new Points(points);
    }

    public Points addPoint(Point point) {
        this.points.add(point);
        return this;
    }

    public Points setPoints(List<Point> points) {
        this.points = points;
        return this;
    }

    /**
     * get influxdb2 write points
     *
     * @return ist<Point>
     */
    public List<Point> getInfluxPoints() {
        return points;
    }

    @Override
    public String toString() {
        return "Points{" +
               "points=" + points +
               '}';
    }
}
