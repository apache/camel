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
package org.apache.camel.component.kafka;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.spi.annotations.SendDynamic;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;

/**
 * Kafka based {@link org.apache.camel.spi.SendDynamicAware} which allows to optimise Kafka components with the toD
 * (dynamic to) DSL in Camel. This implementation optimises by allowing to provide dynamic parameters via
 * {@link KafkaConstants#OVERRIDE_TOPIC} header instead of the endpoint uri. That allows to use a static endpoint and
 * its producer to service dynamic requests.
 */
@SendDynamic("kafka")
public class KafkaSendDynamicAware extends ServiceSupport implements SendDynamicAware {

    private CamelContext camelContext;
    private String scheme;

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public boolean isLenientProperties() {
        return false;
    }

    @Override
    public DynamicAwareEntry prepare(Exchange exchange, String uri, String originalUri) throws Exception {
        return new DynamicAwareEntry(uri, originalUri, null, null);
    }

    @Override
    public String resolveStaticUri(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        String destination = parseTopicName(entry.getUri());
        if (destination != null) {
            String originalDestination = parseTopicName(entry.getOriginalUri());
            if (!destination.equals(originalDestination)) {
                // okay the topic was dynamic, so use the original as endpoint name
                String answer = entry.getUri();
                answer = StringHelper.replaceFirst(answer, destination, originalDestination);
                return answer;
            }
        }
        return null;
    }

    @Override
    public Processor createPreProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        if (exchange.getMessage().getHeader(KafkaConstants.OVERRIDE_TOPIC) != null) {
            return null;
        }

        final String topicName = parseTopicName(entry.getUri());
        return new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getMessage().setHeader(KafkaConstants.OVERRIDE_TOPIC, topicName);
            }
        };
    }

    @Override
    public Processor createPostProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        // no post processor is needed
        return null;
    }

    private String parseTopicName(String uri) {
        // strip query
        uri = StringHelper.before(uri, "?", uri);

        // topic name is after first colon
        int pos = uri.indexOf(':');
        if (pos != -1) {
            uri = uri.substring(pos + 1);
        } else {
            return null;
        }
        if (uri.startsWith("//")) {
            // strip leading slashes
            uri = uri.substring(2);
        }
        return uri;
    }

}
