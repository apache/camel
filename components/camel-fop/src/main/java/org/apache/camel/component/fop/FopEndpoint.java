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
package org.apache.camel.component.fop;

import java.io.InputStream;
import java.net.URI;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ResourceHelper;
import org.apache.fop.apps.FopFactory;

/**
 * The fop component allows you to render a message into different output formats using Apache FOP.
 */
@UriEndpoint(scheme = "fop", title = "FOP", syntax = "fop:outputType", producerOnly = true, label = "transformation")
public class FopEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private FopOutputType outputType;
    @UriParam
    private String userConfigURL;
    @UriParam
    private FopFactory fopFactory;

    public FopEndpoint(String uri, FopComponent component, FopOutputType outputType) {
        super(uri, component);
        this.outputType = outputType;
    }

    public Producer createProducer() throws Exception {
        return new FopProducer(this, fopFactory, outputType.getFormatExtended());
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for FOP endpoint");
    }

    public boolean isSingleton() {
        return true;
    }

    public FopOutputType getOutputType() {
        return outputType;
    }

    /**
     * The primary output format is PDF but other output formats are also supported.
     */
    public void setOutputType(FopOutputType outputType) {
        this.outputType = outputType;
    }

    public String getUserConfigURL() {
        return userConfigURL;
    }

    /**
     * The location of a configuration file which can be loaded from classpath or file system.
     */
    public void setUserConfigURL(String userConfigURL) {
        this.userConfigURL = userConfigURL;
    }

    public FopFactory getFopFactory() {
        return fopFactory;
    }

    /**
     * Allows to use a custom configured or implementation of org.apache.fop.apps.FopFactory.
     */
    public void setFopFactory(FopFactory fopFactory) {
        this.fopFactory = fopFactory;
    }


    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (fopFactory == null && userConfigURL == null) {
            fopFactory = FopFactory.newInstance(new URI("./"));
        } else if (fopFactory != null && userConfigURL != null) {
            throw new FopConfigException("More than one configuration. "
                    + "You can configure fop either by config file or by supplying FopFactory but not both.");
        } else if (fopFactory == null && userConfigURL != null) {
            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), userConfigURL);
            fopFactory = FopFactory.newInstance(new URI(userConfigURL), is);
        }
    }
}
