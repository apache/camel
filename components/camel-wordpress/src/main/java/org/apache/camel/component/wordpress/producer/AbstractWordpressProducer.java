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
package org.apache.camel.component.wordpress.producer;

import java.io.InputStream;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.wordpress.WordpressConfiguration;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.apache.camel.component.wordpress.api.WordpressServiceProvider;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public abstract class AbstractWordpressProducer<T> extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractWordpressProducer.class);
    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    private WordpressConfiguration configuration;

    protected AbstractWordpressProducer(WordpressEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
        if (!WordpressServiceProvider.getInstance().hasAuthentication()) {
            LOG.warn(
                    "Wordpress Producer hasn't authentication. This may lead to errors during route execution. Wordpress writing operations need authentication.");
        }
    }

    public WordpressConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public WordpressEndpoint getEndpoint() {
        return (WordpressEndpoint) super.getEndpoint();
    }

    @Override
    public final void process(Exchange exchange) throws Exception {
        if (this.getConfiguration().getId() == null) {
            exchange.getMessage().setBody(this.processInsert(exchange));
        } else {
            if (this.getEndpoint().getOperationDetail() == null) {
                exchange.getMessage().setBody(this.processUpdate(exchange));
            } else {
                exchange.getMessage().setBody(this.processDelete(exchange));
            }
        }
    }

    /**
     * Gets the message body as the given model class. A JSON text (String, byte[] or InputStream) or a Map is converted
     * with Jackson, the same way as WordPress answers are read. Any other body that cannot be converted fails with an
     * {@link InvalidPayloadException}.
     */
    protected <B> B getMandatoryBody(Exchange exchange, Class<B> type) throws InvalidPayloadException {
        final Message in = exchange.getIn();
        final Object body = in.getBody();
        if (type.isInstance(body)) {
            return type.cast(body);
        }
        try {
            if (body instanceof Map<?, ?> map) {
                return MAPPER.convertValue(map, type);
            }
            if (body instanceof String || body instanceof byte[] || body instanceof InputStream) {
                return MAPPER.readValue(in.getMandatoryBody(String.class), type);
            }
        } catch (InvalidPayloadException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidPayloadException(exchange, type, in, e);
        }
        return in.getMandatoryBody(type);
    }

    protected abstract T processInsert(Exchange exchange) throws Exception;

    protected abstract T processUpdate(Exchange exchange) throws Exception;

    protected abstract T processDelete(Exchange exchange);

}
