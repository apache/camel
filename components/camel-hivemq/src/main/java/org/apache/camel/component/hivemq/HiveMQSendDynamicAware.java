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
package org.apache.camel.component.hivemq;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.spi.annotations.SendDynamic;
import org.apache.camel.support.component.SendDynamicAwareSupport;
import org.apache.camel.util.StringHelper;

/**
 * HiveMQ {@link SendDynamicAware} which allows to optimise the component with the toD (dynamic to) DSL. Dynamic topic
 * names are provided via {@link HiveMQConstants#OVERRIDE_TOPIC} so a single static endpoint/producer can service
 * dynamic requests.
 */
@SendDynamic("hivemq")
public class HiveMQSendDynamicAware extends SendDynamicAwareSupport {

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
        String topic = parseTopicName(entry.getUri());
        if (topic != null) {
            String originalTopic = parseTopicName(entry.getOriginalUri());
            if (!topic.equals(originalTopic)) {
                // topic was dynamic: reuse the original URI (with placeholders) as the static endpoint
                return StringHelper.replaceFirst(entry.getUri(), topic, originalTopic);
            }
        }
        return null;
    }

    @Override
    public Processor createPreProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        if (exchange.getMessage().getHeader(HiveMQConstants.OVERRIDE_TOPIC) != null) {
            return null;
        }

        final String topic = parseTopicName(entry.getUri());
        return new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(HiveMQConstants.OVERRIDE_TOPIC, topic);
            }
        };
    }

    @Override
    public Processor createPostProcessor(Exchange exchange, DynamicAwareEntry entry) throws Exception {
        return null;
    }

    private String parseTopicName(String uri) {
        uri = uri.replaceFirst(getScheme() + "://", ":");
        uri = StringHelper.before(uri, "?", uri);

        int pos = uri.indexOf(':');
        if (pos != -1) {
            return uri.substring(pos + 1);
        }
        return null;
    }
}
