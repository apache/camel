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
package org.apache.camel.component.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This object describes the position on faces where a mask should be placed by default.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingMaskPosition {

    private String point;

    @JsonProperty("x_shift")
    private Float xShift;

    @JsonProperty("y_shift")
    private Float yShift;

    private Float scale;

    public IncomingMaskPosition() {
    }

    public String getPoint() {
        return point;
    }

    public void setPoint(String point) {
        this.point = point;
    }

    public Float getxShift() {
        return xShift;
    }

    public void setxShift(Float xShift) {
        this.xShift = xShift;
    }

    public Float getyShift() {
        return yShift;
    }

    public void setyShift(Float yShift) {
        this.yShift = yShift;
    }

    public Float getScale() {
        return scale;
    }

    public void setScale(Float scale) {
        this.scale = scale;
    }

    @Override
    public String toString() {
        return "IncomingMaskPosition{"
            + "point='" + point + '\''
            + ", xShift=" + xShift
            + ", yShift=" + yShift
            + ", scale=" + scale
            + '}';
    }
}
