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
package org.apache.camel.component.once;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "4.170.0", scheme = "once", title = "Once", syntax = "once:name", consumerOnly = true,
             remote = false, category = { Category.CORE, Category.SCHEDULING })
public class OnceEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    private String name;
    @UriParam(label = "advanced", defaultValue = "1000")
    private long delay = 1000;
    @UriParam
    @Metadata(supportFileReference = true)
    private String body;
    @UriParam(multiValue = true, prefix = "header.")
    @Metadata(supportFileReference = true)
    private Map<String, Object> headers;
    @UriParam(label = "advanced", multiValue = true, prefix = "variable.")
    @Metadata(supportFileReference = true)
    private Map<String, Object> variables;

    public OnceEndpoint() {
    }

    public OnceEndpoint(String endpointUri, Component component, String name) {
        super(endpointUri, component);
        this.name = name;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public OnceComponent getComponent() {
        return (OnceComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new RuntimeCamelException("Cannot produce to a OnceEndpoint: " + getEndpointUri());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new OnceConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public String getName() {
        return name;
    }

    /**
     * The logical name
     */
    public void setName(String name) {
        this.name = name;
    }

    public long getDelay() {
        return delay;
    }

    /**
     * The number of milliseconds to wait before triggering.
     * <p/>
     * The default value is 1000.
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }

    public String getBody() {
        return body;
    }

    /**
     * The data to use as message body. You can externalize the data by using file: or classpath: as prefix and specify
     * the location of the file.
     */
    public void setBody(String body) {
        this.body = body;
    }

    /**
     * The data to use as message headers as key=value pairs. You can externalize the data by using file: or classpath:
     * as prefix and specify the location of the file.
     */
    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    /**
     * The data to use as exchange variables as key=value pairs. You can externalize the data by using file: or
     * classpath: as prefix and specify the location of the file.
     */
    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
}
