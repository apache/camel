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

/**
 * @version 
 */
public class JavaSpaceEndpoint extends DefaultEndpoint {

    private final String remaining;
    private final Map<?, ?> parameters;

    private int concurrentConsumers = 1;
    private String spaceName;
    private boolean transactional;
    private long transactionTimeout = Long.MAX_VALUE;
    private String verb = "take";
    private String templateId;

    public JavaSpaceEndpoint(String endpointUri, String remaining, Map<?, ?> parameters, JavaSpaceComponent component) {
        super(endpointUri, component);
        this.remaining = remaining;
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

    public String getRemaining() {
        return remaining;
    }

    public Map<?, ?> getParameters() {
        return parameters;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new JavaSpaceConsumer(this, processor);
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
