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
package org.apache.camel.component.weka;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import weka.core.Version;

/**
 * Perform machine learning tasks using Weka.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "weka", title = "Weka", syntax = "weka:command", producerOnly = true,
             category = { Category.DATAMINING })
public class WekaEndpoint extends DefaultEndpoint {

    @UriParam
    private final WekaConfiguration configuration;

    public WekaEndpoint(String uri, WekaComponent component, WekaConfiguration config) {
        super(uri, component);
        this.configuration = config;
    }

    public WekaConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public WekaComponent getComponent() {
        return (WekaComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WekaProducer(this);
    }

    String wekaVersion() {
        return Version.VERSION;
    }
}
