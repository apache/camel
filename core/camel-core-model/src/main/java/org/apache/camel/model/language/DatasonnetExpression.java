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

import org.apache.camel.Expression;
import org.apache.camel.spi.Metadata;

/**
 * To use DataSonnet scripts in Camel expressions or predicates.
 */
@Metadata(firstVersion = "3.7.0", label = "language,script", title = "DataSonnet")
@XmlRootElement(name = "datasonnet")
@XmlAccessorType(XmlAccessType.FIELD)
public class DatasonnetExpression extends ExpressionDefinition {

    @XmlAttribute(name = "bodyMediaType")
    private String bodyMediaType;

    @XmlAttribute(name = "outputMediaType")
    private String outputMediaType;

    @XmlAttribute(name = "resultType")
    private String resultTypeName;

    @XmlTransient
    private Class<?> resultType;

    public DatasonnetExpression() {
    }

    public DatasonnetExpression(String expression) {
        super(expression);
    }

    public DatasonnetExpression(Expression expression) {
        super(expression);
    }

    @Override
    public String getLanguage() {
        return "datasonnet";
    }

    public String getBodyMediaType() {
        return bodyMediaType;
    }

    /**
     * The String representation of the message's body MediaType
     */
    public void setBodyMediaType(String bodyMediaType) {
        this.bodyMediaType = bodyMediaType;
    }

    public String getOutputMediaType() {
        return outputMediaType;
    }

    /**
     * The String representation of the MediaType to output
     */
    public void setOutputMediaType(String outputMediaType) {
        this.outputMediaType = outputMediaType;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    /**
     * Sets the class of the result type (type from output).
     * <p/>
     * The default result type is com.datasonnet.document.Document
     */
    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public String getResultTypeName() {
        return resultTypeName;
    }

    /**
     * Sets the class name of the result type (type from output)
     * <p/>
     * The default result type is com.datasonnet.document.Document
     */
    public void setResultTypeName(String resultTypeName) {
        this.resultTypeName = resultTypeName;
    }
}
