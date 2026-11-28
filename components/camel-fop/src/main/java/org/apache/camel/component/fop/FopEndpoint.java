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
package org.apache.camel.component.fop;

import java.io.InputStream;
import java.net.URI;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.fop.apps.FopFactory;

/**
 * Render XSL-FO documents into PDF, PostScript, PNG and other output formats with Apache FOP.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "fop", title = "FOP", syntax = "fop:outputType", producerOnly = true,
             remote = false, category = { Category.FILE, Category.TRANSFORMATION }, headersClass = FopConstants.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO },
                                  format = "xml",
                                  description = "The XSL-FO document to render, as text, bytes, a stream, a file, a DOM"
                                                + " Document or a javax.xml.transform.Source. A Map or no body fails"),
           producerOut = @BodyType(value = Carrier.BYTES, javaType = "java.io.ByteArrayOutputStream",
                                   description = "The rendered document as bytes, in a ByteArrayOutputStream: PDF, or the"
                                                 + " outputType, or the format of the CamelFop.Output.Format header"))
public class FopEndpoint extends DefaultEndpoint {

    @UriPath(description = "The output format to render to. The endpoint URI takes the lower-case name, such as pdf or png,"
                           + " or the MIME type, such as application/pdf. The CamelFop.Output.Format header overrides it for"
                           + " a single message.")
    @Metadata(required = true)
    private FopOutputType outputType;
    @UriParam(description = "The location of an Apache FOP configuration file (such as fop.xconf), for example to set the"
                            + " resolution, the default page size or the fonts. Use the classpath:, file: or http: prefix;"
                            + " without a prefix the file is loaded from the classpath. It cannot be combined with"
                            + " fopFactory.")
    @Metadata(supportFileReference = true)
    private String userConfigURL;
    @UriParam(description = "A custom org.apache.fop.apps.FopFactory to render with, for example one built in Java code with"
                            + " FopFactoryBuilder. It cannot be combined with userConfigURL. If neither is set, a FopFactory"
                            + " with the default FOP settings is created.")
    private FopFactory fopFactory;

    public FopEndpoint(String uri, FopComponent component, FopOutputType outputType) {
        super(uri, component);
        this.outputType = outputType;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new FopProducer(this, fopFactory, outputType.getFormatExtended());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for FOP endpoint");
    }

    public FopOutputType getOutputType() {
        return outputType;
    }

    public void setOutputType(FopOutputType outputType) {
        this.outputType = outputType;
    }

    public String getUserConfigURL() {
        return userConfigURL;
    }

    public void setUserConfigURL(String userConfigURL) {
        this.userConfigURL = userConfigURL;
    }

    public FopFactory getFopFactory() {
        return fopFactory;
    }

    public void setFopFactory(FopFactory fopFactory) {
        this.fopFactory = fopFactory;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (fopFactory == null && userConfigURL == null) {
            fopFactory = FopFactory.newInstance(new URI("./"));
        } else if (fopFactory != null && userConfigURL != null) {
            throw new FopConfigException(
                    "More than one configuration. "
                                         + "You can configure fop either by config file or by supplying FopFactory but not both.");
        } else if (fopFactory == null && userConfigURL != null) {
            if (ResourceHelper.isClasspathUri(userConfigURL)) {
                InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), userConfigURL);
                fopFactory = FopFactory.newInstance(new URI(userConfigURL), is);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (fopFactory == null && userConfigURL != null) {
            if (!ResourceHelper.isClasspathUri(userConfigURL)) {
                InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), userConfigURL);
                fopFactory = FopFactory.newInstance(new URI(userConfigURL), is);
            }
        }
    }
}
