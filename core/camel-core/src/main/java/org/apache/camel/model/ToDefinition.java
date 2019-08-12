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
package org.apache.camel.model;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PropertiesComponent;

/**
 * Sends the message to a static endpoint
 */
@Metadata(label = "eip,endpoint,routing")
@XmlRootElement(name = "to")
@XmlAccessorType(XmlAccessType.FIELD)
public class ToDefinition extends SendDefinition<ToDefinition> implements DefinitionPropertyPlaceholderConfigurable {
    @XmlAttribute
    private ExchangePattern pattern;

    private final Map<String, Supplier<String>> readPlaceholders = new HashMap<>();
    private final Map<String, Consumer<String>> writePlaceholders = new HashMap<>();

    public ToDefinition() {
        readPlaceholders.put("id", this::getId);
        readPlaceholders.put("uri", this::getUri);
        writePlaceholders.put("id", this::setId);
        writePlaceholders.put("uri", this::setUri);
    }

    public ToDefinition(String uri) {
        this();
        setUri(uri);
    }

    public ToDefinition(Endpoint endpoint) {
        this();
        setEndpoint(endpoint);
    }

    public ToDefinition(EndpointProducerBuilder endpointDefinition) {
        this();
        setEndpointProducerBuilder(endpointDefinition);
    }

    public ToDefinition(String uri, ExchangePattern pattern) {
        this(uri);
        this.pattern = pattern;
    }

    public ToDefinition(Endpoint endpoint, ExchangePattern pattern) {
        this(endpoint);
        this.pattern = pattern;
    }

    public ToDefinition(EndpointProducerBuilder endpoint, ExchangePattern pattern) {
        this(endpoint);
        this.pattern = pattern;
    }

    @Override
    public String getShortName() {
        return "to";
    }

    @Override
    public String toString() {
        return "To[" + getLabel() + "]";
    }

    @Override
    public ExchangePattern getPattern() {
        return pattern;
    }

    /**
     * Sets the optional {@link ExchangePattern} used to invoke this endpoint
     */
    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public Map<String, Supplier<String>> getReadPropertyPlaceholderOptions(final CamelContext camelContext) {
        if (getOtherAttributes() != null && !getOtherAttributes().isEmpty()) {
            final Map<String, Supplier<String>> answer = new HashMap<>(readPlaceholders);
            getOtherAttributes().forEach((k, v) -> {
                if (Constants.PLACEHOLDER_QNAME.equals(k.getNamespaceURI())) {
                    if (v instanceof String) {
                        // enforce a properties component to be created if none existed
                        camelContext.getPropertiesComponent(true);

                        // value must be enclosed with placeholder tokens
                        String s = (String) v;
                        String prefixToken = PropertiesComponent.PREFIX_TOKEN;
                        String suffixToken = PropertiesComponent.SUFFIX_TOKEN;

                        if (!s.startsWith(prefixToken)) {
                            s = prefixToken + s;
                        }
                        if (!s.endsWith(suffixToken)) {
                            s = s + suffixToken;
                        }
                        final String value = s;
                        answer.put(k.getLocalPart(), () -> value);
                    }
                }
            });
            return answer;
        } else {
            return readPlaceholders;
        }
    }

    @Override
    public Map<String, Consumer<String>> getWritePropertyPlaceholderOptions(CamelContext camelContext) {
        return writePlaceholders;
    }
}

