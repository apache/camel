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
package org.apache.camel.builder;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.transformer.CustomTransformerDefinition;
import org.apache.camel.model.transformer.DataFormatTransformerDefinition;
import org.apache.camel.model.transformer.EndpointTransformerDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;

/**
 * A <a href="http://camel.apache.org/dsl.html">Java DSL</a> which is
 * used to build a {@link org.apache.camel.spi.Transformer} and register into {@link org.apache.camel.CamelContext}.
 * It requires 'scheme' or a pair of 'from' and 'to' to be specified by scheme(), from() and to() method.
 * And then you can choose a type of transformer by withUri(), withDataFormat(), withJava() or withBean() method.
 */
public class TransformerBuilder {

    private String scheme;
    private String from;
    private String to;
    private String uri;
    private DataFormatDefinition dataFormat;
    private Class<? extends Transformer> clazz;
    private String beanRef;

    /**
     * Set the scheme name supported by the transformer.
     * If you specify 'csv', the transformer will be picked up for all of 'csv' from/to
     * Java transformation. Note that the scheme matching is performed only when
     * no exactly matched transformer exists.
     *
     * @param scheme scheme name
     */
    public TransformerBuilder scheme(String scheme) {
        this.scheme = scheme;
        return this;
    }

    /**
     * Set the 'from' data type name.
     * If you specify 'xml:XYZ', the transformer will be picked up if source type is
     * 'xml:XYZ'. If you specify just 'xml', the transformer matches with all of
     * 'xml' source type like 'xml:ABC' or 'xml:DEF'.
     *
     * @param from 'from' data type name
     */
    public TransformerBuilder fromType(String from) {
        this.from = from;
        return this;
    }

    /**
     * Set the 'from' data type using Java class.
     *
     * @param from 'from' Java class
     */
    public TransformerBuilder fromType(Class<?> from) {
        this.from = new DataType(from).toString();
        return this;
    }

    /**
     * Set the 'to' data type name.
     * If you specify 'json:XYZ', the transformer will be picked up if destination type is
     * 'json:XYZ'. If you specify just 'json', the transformer matches with all of
     * 'json' destination type like 'json:ABC' or 'json:DEF'.
     *
     * @param to 'to' data type
     */
    public TransformerBuilder toType(String to) {
        this.to = to;
        return this;
    }

    /**
     * Set the 'to' data type using Java class.
     *
     * @param to 'to' Java class
     */
    public TransformerBuilder toType(Class<?> to) {
        this.to = new DataType(to).toString();
        return this;
    }

    /**
     * Set the URI to be used for the endpoint {@code Transformer}.
     *
     * @param uri endpoint URI
     */
    public TransformerBuilder withUri(String uri) {
        resetType();
        this.uri = uri;
        return this;
    }

    /**
     * Set the {@code DataFormatDefinition} to be used for the {@code DataFormat} {@code Transformer}.
     */
    public TransformerBuilder withDataFormat(DataFormatDefinition dataFormatDefinition) {
        resetType();
        this.dataFormat = dataFormatDefinition;
        return this;
    }

    /**
     * Set the Java {@code Class} represents a custom {@code Transformer} implementation class.
     */
    public TransformerBuilder withJava(Class<? extends Transformer> clazz) {
        resetType();
        this.clazz = clazz;
        return this;
    }

    /**
     * Set the Java Bean name to be used for custom {@code Transformer}.
     */
    public TransformerBuilder withBean(String ref) {
        resetType();
        this.beanRef = ref;
        return this;
    }

    private void resetType() {
        this.uri = null;
        this.dataFormat = null;
        this.clazz = null;
        this.beanRef = null;
    }

    /**
     * Configure a Transformer according to the configurations built on this builder
     * and register it into given {@code CamelContext}.
     * 
     * @param camelContext {@code CamelContext}
     */
    public void configure(CamelContext camelContext) {
        TransformerDefinition transformer;
        if (uri != null) {
            EndpointTransformerDefinition etd = new EndpointTransformerDefinition();
            etd.setUri(uri);
            transformer = etd;
        } else if (dataFormat != null) {
            DataFormatTransformerDefinition dtd = new DataFormatTransformerDefinition();
            dtd.setDataFormatType(dataFormat);
            transformer = dtd;
        } else if (clazz != null) {
            CustomTransformerDefinition ctd = new CustomTransformerDefinition();
            ctd.setClassName(clazz.getName());
            transformer = ctd;
        } else if (beanRef != null) {
            CustomTransformerDefinition ctd = new CustomTransformerDefinition();
            ctd.setRef(beanRef);
            transformer = ctd;
        } else {
            throw new IllegalArgumentException("No Transformer type was specified");
        }
        
        if (scheme != null) {
            transformer.setScheme(scheme);
        } else {
            transformer.setFromType(from);
            transformer.setToType(to);
        }
        
        camelContext.getTransformers().add(transformer);
    }
}
