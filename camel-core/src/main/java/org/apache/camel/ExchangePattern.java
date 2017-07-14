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
package org.apache.camel;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

/**
 * Represents the kind of message exchange pattern
 *
 * @version 
 */
@XmlType
@XmlEnum
public enum ExchangePattern {
    InOnly, RobustInOnly, InOut, InOptionalOut, OutOnly, RobustOutOnly, OutIn, OutOptionalIn;

    // TODO: We should deprecate and only support InOnly, InOut, and InOptionalOut

    protected static final Map<String, ExchangePattern> MAP = new HashMap<String, ExchangePattern>();

    /**
     * Returns the WSDL URI for this message exchange pattern
     */
    public String getWsdlUri() {
        switch (this) {
        case InOnly:
            return "http://www.w3.org/ns/wsdl/in-only";
        case InOptionalOut:
            return "http://www.w3.org/ns/wsdl/in-opt-out";
        case InOut:
            return "http://www.w3.org/ns/wsdl/in-out";
        case OutIn:
            return "http://www.w3.org/ns/wsdl/out-in";
        case OutOnly:
            return "http://www.w3.org/ns/wsdl/out-only";
        case OutOptionalIn:
            return "http://www.w3.org/ns/wsdl/out-opt-in";
        case RobustInOnly:
            return "http://www.w3.org/ns/wsdl/robust-in-only";
        case RobustOutOnly:
            return "http://www.w3.org/ns/wsdl/robust-out-only";
        default:
            throw new IllegalArgumentException("Unknown message exchange pattern: " + this);
        }
    }

    /**
     * Return true if there can be an IN message
     */
    public boolean isInCapable() {
        switch (this) {
        case OutOnly:
        case RobustOutOnly:
            return false;
        default:
            return true;
        }
    }

    /**
     * Return true if there can be an OUT message
     */
    public boolean isOutCapable() {
        switch (this) {
        case InOnly:
        case RobustInOnly:
            return false;
        default:
            return true;
        }
    }

    /**
     * Return true if there can be a FAULT message
     */
    public boolean isFaultCapable() {
        switch (this) {
        case InOnly:
        case OutOnly:
            return false;
        default:
            return true;
        }
    }

    /**
     * Converts the WSDL URI into a {@link ExchangePattern} instance
     */
    public static ExchangePattern fromWsdlUri(String wsdlUri) {
        return MAP.get(wsdlUri);
    }
    
    public static ExchangePattern asEnum(String value) {
        try {
            return valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown message exchange pattern: " + value, e);
        }
    }

    static {
        for (ExchangePattern mep : values()) {
            String uri = mep.getWsdlUri();
            MAP.put(uri, mep);
            String name = uri.substring(uri.lastIndexOf('/') + 1);
            MAP.put("http://www.w3.org/2004/08/wsdl/" + name, mep);
            MAP.put("http://www.w3.org/2006/01/wsdl/" + name, mep);
        }
    }
}
