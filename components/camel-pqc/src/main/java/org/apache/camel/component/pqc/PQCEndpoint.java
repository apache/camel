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
package org.apache.camel.component.pqc;

import java.security.Security;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Post Quantum Computing Signature and Verification component.
 */
@UriEndpoint(firstVersion = "4.12.0", scheme = "pqc", title = "PQC Algorithms",
             syntax = "pqc:label", producerOnly = true, category = { Category.SECURITY },
             headersClass = PQCConstants.class)
public class PQCEndpoint extends DefaultEndpoint {

    @UriParam
    private PQCConfiguration configuration;

    public PQCEndpoint(String uri, Component component, PQCConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public PQCComponent getComponent() {
        return (PQCComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new PQCProducer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public void doStop() throws Exception {
        Security.removeProvider("BC");
        Security.removeProvider("BCPQC");
        super.doStop();
    }

    public PQCConfiguration getConfiguration() {
        return configuration;
    }
}
