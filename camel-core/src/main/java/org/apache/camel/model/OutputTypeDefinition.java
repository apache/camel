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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.camel.spi.Metadata;

/**
 * Sets data type of the output message.
 * Type name consists of two parts, 'scheme' and 'name' connected with ':'. For Java type 'name'
 * is a fully qualified class name. For example {@code java:java.lang.String}, {@code json:ABCOrder}.
 * It's also possible to specify only scheme part, so that it works like a wildcard. If only 'xml'
 * is specified, all the XML message matches. It's handy to add only one transformer/validator
 * for all the XML-Java transformation.
 * 
 * {@see InputTypeDefinition}
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "outputType")
@XmlAccessorType(XmlAccessType.FIELD)
public class OutputTypeDefinition extends OptionalIdentifiedDefinition<OutputTypeDefinition> {
    @XmlAttribute(required = true)
    private String urn;

    public OutputTypeDefinition() {
    }

    /**
     * Get output type URN.
     * @return output type URN
     */
    public String getUrn() {
        return urn;
    }

    /**
     * Set output type URN.
     * @param urn output type URN
     * @return this OutputTypeDefinition instance
     */
    public void setUrn(String urn) {
        this.urn = urn;
    }

    /**
     * Set output type via Java Class.
     * @param clazz Java Class
     */
    public void setJavaClass(Class<?> clazz) {
        this.urn = "java:" + clazz.getName();
    }

    @Override
    public String toString() {
        return "outputType[" + urn + "]";
    }

    @Override
    public String getLabel() {
        return "outputType[" + urn + "]";
    }

}
