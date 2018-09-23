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
package org.apache.camel.component.iota;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Component for integrate IOTA DLT
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = "iota", title = "IOTA", syntax = "iota:name", label = "dlt")
public class IOTAEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = "true")
    private String name;

    @UriParam
    private String url;

    @UriParam
    private String operation;

    public IOTAEndpoint() {
    }

    public IOTAEndpoint(String uri, IOTAComponent component) {
        super(uri, component);
    }

    public Producer createProducer() throws Exception {
        return new IOTAProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("IOTAConsumer is not supported!");
    }

    public boolean isSingleton() {
        return true;
    }

    public String getName() {
        return name;
    }

    /**
     * Component name
     * 
     * @param url
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    /**
     * Node url
     * 
     * @param url
     */
    public void setUrl(String url) {
        this.url = url;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * Supported operations are 'FIND_TRANSACTION_BY_ADDRESS',
     * 'FIND_TRANSACTION_BY_TAG', 'FIND_TRANSACTION_DATA'
     * 
     * @param operation
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }
}
