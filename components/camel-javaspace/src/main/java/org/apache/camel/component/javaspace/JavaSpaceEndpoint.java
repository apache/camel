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
package org.apache.camel.component.javaspace;

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * @version 
 */
@UriEndpoint(scheme = "javaspace", syntax = "javaspace:url", consumerClass = JavaSpaceConsumer.class, label = "messaging")
public class JavaSpaceEndpoint extends DefaultEndpoint {

    private final Map<?, ?> parameters;

    @UriPath @Metadata(required = "true")
    private final String url;
    @UriParam(defaultValue = "1")
    private int concurrentConsumers = 1;
    @UriParam
    private String spaceName;
    @UriParam(defaultValue = "false")
    private boolean transactional;
    @UriParam
    private long transactionTimeout = Long.MAX_VALUE;
    @UriParam(defaultValue = "take")
    private String verb = "take";
    @UriParam
    private String templateId;

    public JavaSpaceEndpoint(String endpointUri, String remaining, Map<?, ?> parameters, JavaSpaceComponent component) {
        super(endpointUri, component);
        this.url = remaining;
        this.parameters = parameters;
    }
    
    public boolean isTransactional() {
        return transactional;
    }

    public String getVerb() {
        return verb;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }

    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }

    public Producer createProducer() throws Exception {
        return new JavaSpaceProducer(this);
    }

    @Override
    public DefaultExchange createExchange() {
        return new DefaultExchange(getCamelContext(), getExchangePattern());
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * @deprecated use {@link #getUrl()}
     */
    @Deprecated
    public String getRemaining() {
        return url;
    }

    public String getUrl() {
        return url;
    }

    public Map<?, ?> getParameters() {
        return parameters;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        JavaSpaceConsumer answer = new JavaSpaceConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public long getTransactionTimeout() {
        return transactionTimeout;
    }

    public void setTransactionTimeout(long transactionTimeout) {
        this.transactionTimeout = transactionTimeout;
    }

}
