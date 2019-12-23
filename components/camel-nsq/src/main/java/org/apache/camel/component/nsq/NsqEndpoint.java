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
package org.apache.camel.component.nsq;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;

import com.github.brainlag.nsq.NSQConfig;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents a nsq endpoint.
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = "nsq", title = "NSQ", syntax = "nsq:topic", label = "messaging")
public class NsqEndpoint extends DefaultEndpoint {

    @UriParam
    private NsqConfiguration configuration;

    public NsqEndpoint(String uri, NsqComponent component, NsqConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new NsqProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (ObjectHelper.isEmpty(configuration.getTopic())) {
            throw new RuntimeCamelException("Missing required endpoint configuration: topic must be defined for NSQ consumer");
        }
        Consumer consumer = new NsqConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "NsqTopic[" + configuration.getTopic() + "]", configuration.getPoolSize());
    }

    public void setConfiguration(NsqConfiguration configuration) {
        this.configuration = configuration;
    }

    public NsqConfiguration getConfiguration() {
        return configuration;
    }

    public NSQConfig getNsqConfig() throws GeneralSecurityException, IOException {
        NSQConfig nsqConfig = new NSQConfig();

        if (getConfiguration().getSslContextParameters() != null && getConfiguration().isSecure()) {
            SslContext sslContext = new JdkSslContext(getConfiguration().getSslContextParameters().createSSLContext(getCamelContext()), true, null);
            nsqConfig.setSslContext(sslContext);
        }

        if (configuration.getUserAgent() != null && !configuration.getUserAgent().isEmpty()) {
            nsqConfig.setUserAgent(configuration.getUserAgent());
        }

        if (configuration.getMessageTimeout() > -1) {
            nsqConfig.setMsgTimeout((int)configuration.getMessageTimeout());
        }

        return nsqConfig;
    }
}
