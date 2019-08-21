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
package org.apache.camel.model.transformer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.spi.Metadata;

/**
 * Represents an endpoint {@link org.apache.camel.spi.Transformer} which
 * leverages camel {@link org.apache.camel.Endpoint} to perform transformation.
 * A {@link org.apache.camel.impl.transformer.ProcessorTransformer} will be
 * created internally with a {@link org.apache.camel.processor.SendProcessor}
 * which forwards the message to the specified Endpoint. One of the Endpoint
 * 'ref' or 'uri' needs to be specified. {@see TransformerDefinition}
 * {@see ProcessorTransformer}
 */
@Metadata(label = "transformation")
@XmlType(name = "endpointTransformer")
@XmlAccessorType(XmlAccessType.FIELD)
public class EndpointTransformerDefinition extends TransformerDefinition {

    @XmlAttribute
    private String ref;
    @XmlAttribute
    private String uri;

    public String getRef() {
        return ref;
    }

    /**
     * Set the reference of the Endpoint.
     *
     * @param ref reference of the Endpoint
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getUri() {
        return uri;
    }

    /**
     * Set the URI of the Endpoint.
     *
     * @param uri URI of the Endpoint
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

}
