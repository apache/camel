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
package org.apache.camel.dataformat.hessian;

import java.io.Serializable;
import java.util.Objects;

/** A simple object used used by {@link HessianDataFormatMarshallingTest}. */
class TestObject implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean bool;
    private int intNumber;
    private double floatNumber;
    private char character;
    private String text;

    public boolean isBool() {
        return bool;
    }

    public void setBool(final boolean bool) {
        this.bool = bool;
    }

    public int getIntNumber() {
        return intNumber;
    }

    public void setIntNumber(final int intNumber) {
        this.intNumber = intNumber;
    }

    public double getFloatNumber() {
        return floatNumber;
    }

    public void setFloatNumber(final double floatNumber) {
        this.floatNumber = floatNumber;
    }

    public char getCharacter() {
        return character;
    }

    public void setCharacter(final char character) {
        this.character = character;
    }

    public String getText() {
        return text;
    }

    public void setText(final String text) {
        this.text = text;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final TestObject that = (TestObject) o;

        return Objects.equals(bool, that.bool)
                && Objects.equals(intNumber, that.intNumber)
                && Objects.equals(floatNumber, that.floatNumber)
                && Objects.equals(character, that.character)
                && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = bool ? 1 : 0;
        result = 31 * result + intNumber;
        temp = Double.doubleToLongBits(floatNumber);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + character;
        result = 31 * result + (text != null ? text.hashCode() : 0);
        return result;
    }
}
