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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Sending and receiving messages through JavaSpace.
 */
@UriEndpoint(firstVersion = "2.1.0", scheme = "javaspace", title = "JavaSpace", syntax = "javaspace:url", consumerClass = JavaSpaceConsumer.class, label = "messaging")
public class JavaSpaceEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private final String url;
    @UriParam @Metadata(required = "true")
    private String spaceName;
    @UriParam(label = "consumer", defaultValue = "1")
    private int concurrentConsumers = 1;
    @UriParam(label = "consumer", defaultValue = "take", enums = "take,read")
    private String verb = "take";
    @UriParam(label = "consumer")
    private String templateId;
    @UriParam
    private boolean transactional;
    @UriParam
    private long transactionTimeout = Long.MAX_VALUE;

    public JavaSpaceEndpoint(String endpointUri, String remaining, JavaSpaceComponent component) {
        super(endpointUri, component);
        this.url = remaining;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        JavaSpaceConsumer answer = new JavaSpaceConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public Producer createProducer() throws Exception {
        return new JavaSpaceProducer(this);
    }

    public boolean isSingleton() {
        return true;
    }

    public String getVerb() {
        return verb;
    }

    /**
     * Specifies the verb for getting JavaSpace entries.
     */
    public void setVerb(String verb) {
        this.verb = verb;
    }

    public boolean isTransactional() {
        return transactional;
    }

    /**
     * If true, sending and receiving entries is performed within a transaction.
     */
    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }

    /**
     * The URL to the JavaSpace server
     */
    public String getUrl() {
        return url;
    }

    /**
     * Specifies the number of concurrent consumers getting entries from the JavaSpace.
     */
    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public String getSpaceName() {
        return spaceName;
    }

    /**
     * Specifies the JavaSpace name.
     */
    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public String getTemplateId() {
        return templateId;
    }

    /**
     * If present, this option specifies the Spring bean ID of the template to use for reading/taking entries.
     */
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public long getTransactionTimeout() {
        return transactionTimeout;
    }

    /**
     * Specifies the transaction timeout in millis. By default there is no timeout.
     */
    public void setTransactionTimeout(long transactionTimeout) {
        this.transactionTimeout = transactionTimeout;
    }

}
