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
package org.apache.camel.component.xj;

import com.fasterxml.jackson.core.JsonFactory;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.xslt.XsltBuilder;
import org.apache.camel.component.xslt.saxon.XsltSaxonBuilder;
import org.apache.camel.component.xslt.saxon.XsltSaxonEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Transform JSON and XML message using a XSLT.
 */
@ManagedResource(description = "Managed XJEndpoint")
@UriEndpoint(firstVersion = "3.0.0", scheme = "xj", title = "XJ", syntax = "xj:resourceUri", producerOnly = true,
             category = { Category.TRANSFORMATION }, headersClass = XJConstants.class)
public class XJEndpoint extends XsltSaxonEndpoint {

    private final JsonFactory jsonFactory = new JsonFactory();

    @UriParam
    @Metadata(required = true, description = "Transform direction. Either XML2JSON or JSON2XML")
    private TransformDirection transformDirection;

    public XJEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @ManagedAttribute(description = "Transform direction")
    public TransformDirection getTransformDirection() {
        return transformDirection;
    }

    /**
     * Sets the transform direction.
     */
    public void setTransformDirection(TransformDirection transformDirection) {
        this.transformDirection = transformDirection;
    }

    @Override
    protected void doInit() throws Exception {
        if ("identity".equalsIgnoreCase(getResourceUri())) {
            // Using a stylesheet for "identity" transform is slow. but with a {@link TransformerFactory}
            // we can't get an identity transformer. But for now we leave it that way.
            setResourceUri("org/apache/camel/component/xj/identity.xsl");
        }

        super.doInit();
    }

    @Override
    protected XsltSaxonBuilder createXsltBuilder() throws Exception {
        final XsltSaxonBuilder xsltBuilder = super.createXsltBuilder();
        xsltBuilder.setAllowStAX(true); // we rely on stax so always to true.

        configureInput(xsltBuilder);

        return xsltBuilder;
    }

    /**
     * Configures the source input depending on the {@link XJEndpoint#transformDirection}
     */
    protected void configureInput(XsltBuilder xsltBuilder) {
        if (TransformDirection.JSON2XML == this.transformDirection) {
            final JsonSourceHandlerFactoryImpl sourceHandlerFactory = new JsonSourceHandlerFactoryImpl(jsonFactory);
            sourceHandlerFactory.setFailOnNullBody(isFailOnNullBody());
            xsltBuilder.setSourceHandlerFactory(sourceHandlerFactory);
        }
        // in the other direction, XML2JSON, the default org.apache.camel.component.xslt.XmlSourceHandlerFactoryImpl will be used
    }

    /**
     * Configures the result output depending on the {@link XJEndpoint#transformDirection}
     */
    @Override
    protected void configureOutput(XsltBuilder xsltBuilder, String output) throws Exception {
        switch (this.transformDirection) {
            case JSON2XML:
                super.configureOutput(xsltBuilder, output);
                break;
            case XML2JSON:
                configureJsonOutput(xsltBuilder, output);
                break;
            default:
                throw new IllegalArgumentException("Unknown transformation direction: " + this.transformDirection);
        }
    }

    /**
     * Configures the result output when transforming to JSON
     */
    protected void configureJsonOutput(XsltBuilder xsltBuilder, String output) {
        if ("DOM".equals(output)) {
            throw new UnsupportedOperationException("DOM output not supported when transforming to json");
        } else if ("bytes".equals(output)) {
            xsltBuilder.setResultHandlerFactory(new JsonStreamResultHandlerFactory(jsonFactory));
        } else if ("file".equals(output)) {
            xsltBuilder.setResultHandlerFactory(new JsonFileResultHandlerFactory(jsonFactory));
        } else {
            xsltBuilder.setResultHandlerFactory(new JsonStringResultHandlerFactory(jsonFactory));
        }
    }
}
