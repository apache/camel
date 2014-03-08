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

import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.SAXException;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.DefaultConfigurationBuilder;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ResourceHelper;
import org.apache.fop.apps.FopFactory;

/**
 * Represents a Fop endpoint.
 */
public class FopEndpoint extends DefaultEndpoint {
    private String userConfigURL;
    private FopFactory fopFactory;
    private String remaining;

    public FopEndpoint(String uri, FopComponent component, String remaining) {
        super(uri, component);
        this.remaining = remaining;
    }

    public Producer createProducer() throws Exception {
        return new FopProducer(this, fopFactory, remaining);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for FOP endpoint");
    }

    public boolean isSingleton() {
        return true;
    }

    FopFactory getFopFactory() {
        return fopFactory;
    }

    public void setUserConfigURL(String userConfigURL) {
        this.userConfigURL = userConfigURL;
    }

    private static void updateConfigurations(InputStream is, FopFactory fopFactory) throws SAXException, IOException, ConfigurationException {
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
