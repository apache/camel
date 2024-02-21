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
package org.apache.camel.component.tika;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Parse documents and extract metadata and text using Apache Tika.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "tika", title = "Tika", syntax = "tika:operation", producerOnly = true,
             remote = false, category = { Category.DOCUMENT, Category.TRANSFORMATION })
public class TikaEndpoint extends DefaultEndpoint {

    @UriParam
    private TikaConfiguration tikaConfiguration;

    public TikaEndpoint(String endpointUri, Component component, TikaConfiguration tikaConfiguration) {
        super(endpointUri, component);
        this.tikaConfiguration = tikaConfiguration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new TikaProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer does not supported for Tika component:" + getEndpointUri());
    }

    public TikaConfiguration getTikaConfiguration() {
        return tikaConfiguration;
    }

    public void setTikaConfiguration(TikaConfiguration tikaConfiguration) {
        this.tikaConfiguration = tikaConfiguration;
    }

}
