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

/** A simple object used used by {@link HessianDataFormatMarshallingTest}. */
class AnotherObject implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean bool;
    private int intNumber;

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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (bool ? 1231 : 1237);
        result = prime * result + intNumber;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AnotherObject other = (AnotherObject)obj;
        if (bool != other.bool) {
            return false;
        }
        if (intNumber != other.intNumber) {
            return false;
        }
        return true;
    }
}
