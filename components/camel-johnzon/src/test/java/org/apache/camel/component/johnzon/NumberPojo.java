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
package org.apache.camel.component.johnzon;

import java.math.BigDecimal;

public class NumberPojo {
    private BigDecimal bg;
    private int intNumber;
    private long longNumber;
    private double doubleNumber;
    private float floatNumber;
    private boolean bool;

    public BigDecimal getBg() {
        return bg;
    }

    public void setBg(final BigDecimal bg) {
        this.bg = bg;
    }

    public int getIntNumber() {
        return intNumber;
    }

    public void setIntNumber(final int intNumber) {
        this.intNumber = intNumber;
    }

    public long getLongNumber() {
        return longNumber;
    }

    public void setLongNumber(final long longNumber) {
        this.longNumber = longNumber;
    }

    public double getDoubleNumber() {
        return doubleNumber;
    }

    public void setDoubleNumber(final double doubleNumber) {
        this.doubleNumber = doubleNumber;
    }

    public float getFloatNumber() {
        return floatNumber;
    }

    public void setFloatNumber(final float floatNumber) {
        this.floatNumber = floatNumber;
    }

    public boolean isBool() {
        return bool;
    }

    public void setBool(final boolean bool) {
        this.bool = bool;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((bg == null) ? 0 : bg.hashCode());
        result = prime * result + (bool ? 1231 : 1237);
        long temp;
        temp = Double.doubleToLongBits(doubleNumber);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + Float.floatToIntBits(floatNumber);
        result = prime * result + intNumber;
        result = prime * result + (int) (longNumber ^ (longNumber >>> 32));
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NumberPojo other = (NumberPojo) obj;
        if (bg == null) {
            if (other.bg != null) {
                return false;
            }
        } else if (!bg.equals(other.bg)) {
            return false;
        }
        if (bool != other.bool) {
            return false;
        }
        if (Double.doubleToLongBits(doubleNumber) != Double.doubleToLongBits(other.doubleNumber)) {
            return false;
        }
        if (Float.floatToIntBits(floatNumber) != Float.floatToIntBits(other.floatNumber)) {
            return false;
        }
        if (intNumber != other.intNumber) {
            return false;
        }
        if (longNumber != other.longNumber) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "NumberPojo [bg=" + bg + ", intNumber=" + intNumber + ", longNumber=" + longNumber + ", doubleNumber="
            + doubleNumber + ", floatNumber=" + floatNumber + ", bool=" + bool + "]";
    }
}
