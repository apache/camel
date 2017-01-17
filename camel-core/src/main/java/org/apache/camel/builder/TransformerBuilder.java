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
import org.apache.camel.spi.Transformer;

/**
 * A <a href="http://camel.apache.org/dsl.html">Java DSL</a> which is
 * used to build a {@link org.apache.camel.spi.Transformer} and register into {@link org.apache.camel.CamelContext}.
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
     * Set a scheme name supported by the transformer.
     *
     * @param scheme scheme name
     */
    public TransformerBuilder scheme(String scheme) {
        this.scheme = scheme;
        return this;
    }

    /**
     * Set the 'from' data type .
     *
     * @param from 'from' data type
     */
    public TransformerBuilder from(String from) {
        this.from = from;
        return this;
    }

    /**
     * Set the 'from' data type using Java class.
     *
     * @param clazz 'from' Java class
     */
    public TransformerBuilder from(Class<?> from) {
        this.from = "java:" + from.getName();
        return this;
    }

    /**
     * Set the 'to' data type .
     *
     * @param to 'to' data type
     */
    public TransformerBuilder to(String to) {
        this.to = to;
        return this;
    }

    /**
     * Set the 'to' data type using Java class.
     *
     * @param clazz 'to' Java class
     */
    public TransformerBuilder to(Class<?> to) {
        this.to = "java:" + to.getName();
        return this;
    }

    /**
     * Set the URI to be used for the endpoint {@code Transformer}.
     * @see {@code EndpointTransformerDefinition}, {@code ProcessorTransformer}
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
     * @see {@code DataFormatTransformerDefinition}, {@code DataFormatTransformer}
     * 
     * @param dfd {@code DataFormatDefinition}
     */
    public TransformerBuilder withDataFormat(DataFormatDefinition dfd) {
        resetType();
        this.dataFormat = dfd;
        return this;
    }

    /**
     * Set the Java {@code Class} represents a custom {@code Transformer} implementation class
     * to be used for custom Transformer.
     * @see {@code CustomTransformerDefinition}
     * 
     * @param clazz {@code Class} object represents custom transformer implementation
     */
    public TransformerBuilder withJava(Class<? extends Transformer> clazz) {
        resetType();
        this.clazz = clazz;
        return this;
    }

    /**
     * Set the Java Bean name to be used for custom {@code Transformer}.
     * @see {@code CustomTransformerDefinition}
     * 
     * @param ref bean name for the custom {@code Transformer}
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
            ctd.setType(clazz.getName());
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
            transformer.setFrom(from);
            transformer.setTo(to);
        }
        
        camelContext.getTransformers().add(transformer);
    }
}
