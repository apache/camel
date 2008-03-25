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
package org.apache.camel.converter.jaxb;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @version $Revision$
 */
@XmlRootElement(name = "intHeader")
@XmlAccessorType(value = XmlAccessType.FIELD)
public class IntegerHeader extends HeaderType {
    @XmlAttribute(name = "value")
    private Integer number;

    public IntegerHeader() {
    }

    public IntegerHeader(String name, Integer number) {
        super(name);
        this.number = number;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public Object getValue() {
        return getNumber();
    }

    public void setValue(Object value) {
        if (value instanceof Number) {
            Number n = (Number) value;
            setNumber(n.intValue());
        } else {
            throw new IllegalArgumentException("Value must be an Integer");
        }
    }
}