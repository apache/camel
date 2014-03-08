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
package org.apache.camel.dataformat.bindy.model.fix.complex.onetomany;

import org.apache.camel.dataformat.bindy.annotation.KeyValuePairField;

// @Message(keyValuePairSeparator = "=", pairSeparator = "\\u0001", type = "FIX", version = "4.1")
public class Security {

    @KeyValuePairField(tag = 22)
    // Fund ID type (Sedol, ISIN, ...)
    private String idSource;

    @KeyValuePairField(tag = 48)
    // Fund code
    private String securityCode;

    @KeyValuePairField(tag = 54)
    // Movement type ( 1 = Buy, 2 = sell)
    private String side;

    public String getIdSource() {
        return idSource;
    }

    public void setIdSource(String source) {
        this.idSource = source;
    }

    public String getSecurityCode() {
        return securityCode;
    }

    public void setSecurityCode(String securityCode) {
        this.securityCode = securityCode;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    @Override
    public String toString() {
        return Security.class.getName() + " --> 22: " + this.getIdSource() + ", 48: " + this.getSecurityCode() + ", 54: " + this.getSide();
    }

}
