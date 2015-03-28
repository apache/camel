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

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.DefaultConfigurationBuilder;
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
 * Represents a Fop endpoint.
 */
@UriEndpoint(scheme = "fop", title = "FOP", syntax = "fop:outputFormat", producerOnly = true, label = "transformation")
public class FopEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private String outputFormat;
    @UriParam
    private String userConfigURL;
    @UriParam
    private FopFactory fopFactory;

    public FopEndpoint(String uri, FopComponent component, String outputFormat) {
        super(uri, component);
        this.outputFormat = outputFormat;
    }

    public Producer createProducer() throws Exception {
        return new FopProducer(this, fopFactory, outputFormat);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for FOP endpoint");
    }

    public boolean isSingleton() {
        return true;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
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

    private static void updateConfigurations(InputStream is, FopFactory fopFactory) throws Exception {
        DefaultConfigurationBuilder cfgBuilder = new DefaultConfigurationBuilder();
        Configuration cfg = cfgBuilder.build(is);
        fopFactory.setUserConfig(cfg);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (fopFactory == null) {
            fopFactory = FopFactory.newInstance();
        }

        if (userConfigURL != null) {
            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext().getClassResolver(), userConfigURL);
            updateConfigurations(is, fopFactory);
        }
    }
}
