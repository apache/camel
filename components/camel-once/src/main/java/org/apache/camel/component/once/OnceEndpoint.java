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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Send a single message to the route once, after the route starts, which is useful for development and testing.
 */
@UriEndpoint(firstVersion = "4.17.0", scheme = "once", title = "Once", syntax = "once:name", consumerOnly = true,
             remote = false, category = { Category.CORE, Category.SCHEDULING })
@BodyTypes(consumerOut = @BodyType(value = Carrier.NULL, dependsOn = "body",
                                   description = "No body without the body option. The body option gives its text, the text of"
                                                 + " a file:, classpath: or http: resource, a Boolean for true or false, a"
                                                 + " number for a whole number, or with language:NAME: the result of the"
                                                 + " expression"))
public class OnceEndpoint extends DefaultEndpoint {

    @UriPath(description = "A name of your choice for the endpoint. The consumer also uses it in the name of the thread that"
                           + " sends the message.")
    @Metadata(required = true)
    private String name;
    @UriParam(label = "advanced", defaultValue = "1000",
              description = "The number of milliseconds that the consumer waits, after the route has started, before it sends"
                            + " the message. Use 0 or a negative value to send the message at once. If not set, the delay"
                            + " option of the component applies.")
    private long delay = 1000;
    @UriParam(description = "The body of the message. The text true or false (in any case) becomes a Boolean, and a whole"
                            + " number becomes an Integer, or a Long if it is too large for an Integer. A value with a resource"
                            + " prefix such as file:, classpath: or http: is replaced by the text of that resource, and a value"
                            + " that starts with language:NAME: is evaluated as an expression, as described for the languages"
                            + " option of the component. The consumer sets the body last, so an expression can use the"
                            + " headers, exchange properties and variables. If not set, the body is null.")
    @Metadata(supportFileReference = true)
    private String body;
    @UriParam(multiValue = true, prefix = "header.",
              description = "The headers of the message, as key=value pairs, for example header.foo=abc. Each value is"
                            + " converted, loaded from a resource or evaluated as an expression in the same way as the body"
                            + " option. A value that results in null sets no header.")
    @Metadata(supportFileReference = true)
    private Map<String, String> headers;
    @UriParam(label = "advanced", multiValue = true, prefix = "variable.",
              description = "The exchange variables, as key=value pairs, for example variable.foo=abc. Each value is"
                            + " converted, loaded from a resource or evaluated as an expression in the same way as the body"
                            + " option. The consumer sets the variables first, so an expression in the other options can use"
                            + " them.")
    @Metadata(supportFileReference = true)
    private Map<String, String> variables;
    @UriParam(label = "advanced", multiValue = true, prefix = "exchangeProperty.",
              description = "The exchange properties, as key=value pairs, for example exchangeProperty.foo=abc. Each value is"
                            + " converted, loaded from a resource or evaluated as an expression in the same way as the body"
                            + " option. The consumer sets them after the variables and before the headers and the body.")
    @Metadata(supportFileReference = true)
    private Map<String, String> exchangeProperties;

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

    public void setName(String name) {
        this.name = name;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }

    public Map<String, String> getExchangeProperties() {
        return exchangeProperties;
    }

    public void setExchangeProperties(Map<String, String> exchangeProperties) {
        this.exchangeProperties = exchangeProperties;
    }
}
