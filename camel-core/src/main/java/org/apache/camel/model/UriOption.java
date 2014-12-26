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
package org.apache.camel.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

/**
 * Represents an XML &lt;option&gt; element within a &lt;from/&gt; or a &lt;to/&gt; element
 *
 * @version
 */
@XmlRootElement(name = "option")
@XmlAccessorType(XmlAccessType.FIELD)
public class UriOption {
    @XmlAttribute
    private String key;
    @XmlValue
    private String value;

    public UriOption() {
    }

    public UriOption(String key, String value) {
        setKey(key);
        setValue(value);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    // Utility methods
    // -----------------------------------------------------------------------
    protected static Map<String, Object> transformOptions(List<UriOption> options) {
        if (options == null) {
            return null;
        }

        Map<String, Object> result = new HashMap<String, Object>();
        for (UriOption option : options) {
            result.put(option.getKey(), option.getValue());
        }
        return result;
    }
}
