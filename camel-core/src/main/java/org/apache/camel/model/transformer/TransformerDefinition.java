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
package org.apache.camel.model.transformer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Transformer;

/**
 * <p>Represents a {@link Transformer} which declaratively transforms message content
 * according to the input type declared by {@link InputTypeDefinition} and/or output type
 * declared by {@link OutputTypeDefinition}.</p>
 * <p>If you specify from='java:com.example.ABC' and to='xml:XYZ', the transformer
 * will be picked up when current message type is 'java:com.example.ABC' and expected
 * message type is 'xml:XYZ'.
 * If you specify from='java' to='xml', then it will be picked up for all of java
 * to xml transformation.
 * Also it's possible to specify scheme='xml' so that the transformer will be picked up
 * for all of java to xml and xml to java transformation.</p>
 * 
 * {@see Transformer}
 * {@see InputTypeDefinition}
 * {@see OutputTypeDefinition}
 */
@Metadata(label = "transformation")
@XmlType(name = "transformer")
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class TransformerDefinition {
    @XmlAttribute
    private String scheme;
    @XmlAttribute
    private String from;
    @XmlAttribute
    private String to;

    public Transformer createTransformer(CamelContext context) throws Exception {
        return doCreateTransformer(context);
    };

    protected abstract Transformer doCreateTransformer(CamelContext context) throws Exception;

    public String getScheme() {
        return scheme;
    }

    /**
     * Set a scheme name supported by the transformer.
     * @param scheme scheme name
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getFrom() {
        return from;
    }

    /**
     * Set the 'from' data type .
     * @param from 'from' data type
     */
    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * Set the 'from' data type using Java class.
     * @param clazz 'from' Java class
     */
    public void setFrom(Class<?> clazz) {
        this.from = "java:" + clazz.getName();
    }

    public String getTo() {
        return to;
    }

    /**
     * Set the 'to' data type.
     * @param to 'to' data type 
     */
    public void setTo(String to) {
        this.to = to;
    }

    /**
     * Set the 'to' data type using Java class.
     * @param clazz 'to' Java class
     */
    public void setTo(Class<?> clazz) {
        this.to = "java:" + clazz.getName();
    }

}

