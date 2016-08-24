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
package org.apache.camel.swagger.component;

import java.io.InputStream;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ResourceHelper.resolveMandatoryResourceAsInputStream;

@UriEndpoint(scheme = "swagger", title = "Swagger", syntax = "swagger:schema:verb:path",
        producerOnly = true, label = "rest", lenientProperties = true)
public class SwaggerEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(SwaggerEndpoint.class);

    private transient Swagger swagger;

    @UriPath(enums = "http,https")
    private String schema;
    @UriPath(enums = "get,put,post,head,delete,patch,options") @Metadata(required = "true")
    private String verb;
    @UriPath @Metadata(required = "true")
    private String path;
    @UriParam
    private String componentName;
    @UriParam
    private String host;
    @UriParam(multiValue = true)
    private String queryParameters;

    public SwaggerEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        SwaggerProducer answer = new SwaggerProducer(this);
        answer.setSwagger(swagger);
        return answer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public String getSchema() {
        return schema;
    }

    /**
     * Scheme to use when calling the REST service such as http or https
     */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getVerb() {
        return verb;
    }

    /**
     * Verb of the HTTP service such as get,post,put etc.
     */
    public void setVerb(String verb) {
        this.verb = verb;
    }

    public String getPath() {
        return path;
    }

    /**
     * Uri template (context-path) of HTTP service to call
     */
    public void setPath(String path) {
        this.path = path;
    }

    public String getQueryParameters() {
        return queryParameters;
    }

    /**
     * Query parameters for the HTTP service to call
     */
    public void setQueryParameters(String queryParameters) {
        this.queryParameters = queryParameters;
    }

    public String getComponentName() {
        return componentName;
    }

    /**
     * The camel component to use as HTTP client for calling the REST service.
     * The default value is: http
     */
    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public String getHost() {
        return host;
    }

    /**
     * Host and port of HTTP service to use (override host in swagger schema)
     */
    public void setHost(String host) {
        this.host = host;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // load json model
        ObjectHelper.notEmpty(schema, "schema");

        InputStream is = resolveMandatoryResourceAsInputStream(getCamelContext(), schema);
        try {
            SwaggerParser parser = new SwaggerParser();
            String json = getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, is);
            LOG.debug("Loaded swagger schema:\n{}", json);
            swagger = parser.parse(json);
        } finally {
            IOHelper.close(is);
        }

    }
}
