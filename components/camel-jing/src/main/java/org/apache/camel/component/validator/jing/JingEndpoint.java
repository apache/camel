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
package org.apache.camel.component.validator.jing;

import java.io.InputStream;

import org.xml.sax.InputSource;

import com.thaiopensource.relaxng.SchemaFactory;
import com.thaiopensource.validate.Schema;
import com.thaiopensource.xml.sax.Jaxp11XMLReaderCreator;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.StringHelper;

/**
 * Validates the payload of a message using RelaxNG Syntax using Jing library.
 */
@UriEndpoint(firstVersion = "1.1.0", scheme = "jing", title = "Jing", syntax = "jing:resourceUri", producerOnly = true, label = "validation")
public class JingEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = true)
    private String resourceUri;
    @UriParam
    private boolean compactSyntax;

    private Schema schema;
    private SchemaFactory schemaFactory;
    private InputSource inputSource;

    public JingEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        JingValidator answer = new JingValidator(this);
        answer.setSchema(getSchema());
        return answer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("This endpoint does not support consumer");
    }

    public String getResourceUri() {
        return resourceUri;
    }

    /**
     * URL to a local resource on the classpath or a full URL to a remote resource or resource on the file system which contains the schema to validate against.
     */
    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public boolean isCompactSyntax() {
        return compactSyntax;
    }

    /**
     * Whether to validate using RelaxNG compact syntax or not.
     * <p/>
     * By default this is <tt>false</tt> for using RelaxNG XML Syntax (rng)
     * And <tt>true</tt> is for using  RelaxNG Compact Syntax (rnc)
     */
    public void setCompactSyntax(boolean compactSyntax) {
        this.compactSyntax = compactSyntax;
    }

    public Schema getSchema() {
        return schema;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    public SchemaFactory getSchemaFactory() {
        return schemaFactory;
    }

    public void setSchemaFactory(SchemaFactory schemaFactory) {
        this.schemaFactory = schemaFactory;
    }

    public InputSource getInputSource() {
        return inputSource;
    }

    public void setInputSource(InputSource inputSource) {
        this.inputSource = inputSource;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (inputSource == null) {
            StringHelper.notEmpty(resourceUri, "resourceUri", this);
            InputStream inputStream = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), resourceUri);
            inputSource = new InputSource(inputStream);
        }

        if (schemaFactory == null) {
            schemaFactory = new SchemaFactory();
            schemaFactory.setCompactSyntax(compactSyntax);
            schemaFactory.setXMLReaderCreator(new Jaxp11XMLReaderCreator());
        }

        if (schema == null) {
            schema = schemaFactory.createSchema(inputSource);
        }
    }

}
