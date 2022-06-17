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
package org.apache.camel.model.language;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Evaluates a JQ expression against a JSON message body.
 */
@Metadata(firstVersion = "3.18.0", label = "language,json", title = "JQ")
@XmlRootElement(name = "jq")
@XmlAccessorType(XmlAccessType.FIELD)
public class JqExpression extends ExpressionDefinition {
    @XmlAttribute(name = "resultType")
    private String resultTypeName;
    @XmlTransient
    private Class<?> resultType;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String headerName;
    @Metadata(label = "advanced")
    private String propertyName;

    public JqExpression() {
    }

    public JqExpression(String expression) {
        super(expression);
    }

    @Override
    public String getLanguage() {
        return "jq";
    }

    public String getResultTypeName() {
        return resultTypeName;
    }

    /**
     * Sets the class of the result type (type from output)
     */
    public void setResultTypeName(String resultTypeName) {
        this.resultTypeName = resultTypeName;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    /**
     * Sets the class name of the result type (type from output)
     */
    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * Name of header to use as input, instead of the message body
     * </p>
     * It has as higher precedent than the propertyName if both are set.
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Name of property to use as input, instead of the message body.
     * </p>
     * It has a lower precedent than the headerName if both are set.
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }
}
