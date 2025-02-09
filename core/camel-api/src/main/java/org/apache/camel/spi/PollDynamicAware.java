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
package org.apache.camel.spi;

import java.util.Map;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Service;

/**
 * Used for components that can optimise the usage of {@link org.apache.camel.processor.PollProcessor} (poll/pollEnrich)
 * to reuse a static {@link Endpoint} and {@link org.apache.camel.DynamicPollingConsumer} that supports using headers to
 * provide the dynamic parts. For example many of the file based components supports this.
 */
public interface PollDynamicAware extends Service, CamelContextAware {

    /**
     * Sets the component name.
     *
     * @param scheme name of the component
     */
    void setScheme(String scheme);

    /**
     * Gets the component name
     */
    String getScheme();

    /**
     * Whether to traverse the given parameters, and resolve any parameter values which uses the RAW token syntax:
     * <tt>key=RAW(value)</tt>. And then remove the RAW tokens, and replace the content of the value, with just the
     * value.
     */
    default boolean resolveRawParameterValues() {
        return true;
    }

    /**
     * Whether the endpoint is lenient or not.
     *
     * @see Endpoint#isLenientProperties()
     */
    boolean isLenientProperties();

    /**
     * An entry of detailed information from the recipient uri, which allows the {@link PollDynamicAware} implementation
     * to prepare the static uri to be used for the optimised poll.
     */
    class DynamicAwareEntry {

        private final String uri;
        private final String originalUri;
        private final Map<String, Object> properties;
        private final Map<String, Object> lenientProperties;

        public DynamicAwareEntry(String uri, String originalUri, Map<String, Object> properties,
                                 Map<String, Object> lenientProperties) {
            this.uri = uri;
            this.originalUri = originalUri;
            this.properties = properties;
            this.lenientProperties = lenientProperties;
        }

        public String getUri() {
            return uri;
        }

        public String getOriginalUri() {
            return originalUri;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public Map<String, Object> getLenientProperties() {
            return lenientProperties;
        }
    }

    /**
     * Prepares for using optimised dynamic polling consumer by parsing the uri and returning an entry of details.
     *
     * @param  exchange    the exchange
     * @param  uri         the resolved uri which is intended to be used
     * @param  originalUri the original uri of the endpoint before any dynamic evaluation
     * @return             prepared information about the dynamic endpoint to use
     * @throws Exception   is thrown if error parsing the uri
     */
    DynamicAwareEntry prepare(Exchange exchange, String uri, String originalUri) throws Exception;

    /**
     * Resolves the static part of the uri that are used for creating a single {@link Endpoint} and
     * {@link org.apache.camel.DynamicPollingConsumer} that will be reused for processing the optimised poll/pollEnrich.
     *
     * @param  exchange  the exchange
     * @param  entry     prepared information about the dynamic endpoint to use
     * @return           the static uri, or <tt>null</tt> to not let poll/pollEnrich use this optimisation.
     * @throws Exception is thrown if error resolving the static uri.
     */
    String resolveStaticUri(Exchange exchange, DynamicAwareEntry entry) throws Exception;

}
