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
package org.apache.camel.component.bonita;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.bonita.exception.BonitaException;
import org.apache.camel.component.bonita.producer.BonitaStartProducer;
import org.apache.camel.component.bonita.util.BonitaOperation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Communicate with a remote Bonita BPM process engine.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "bonita", title = "Bonita", syntax = "bonita:operation", producerOnly = true,
             category = { Category.WORKFLOW })
public class BonitaEndpoint extends DefaultEndpoint {

    @UriParam
    private BonitaConfiguration configuration;

    public BonitaEndpoint() {
    }

    public BonitaEndpoint(String uri, BonitaComponent component,
                          BonitaConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        if (configuration.getOperation() == BonitaOperation.startCase) {
            return new BonitaStartProducer(this, configuration);
        } else {
            throw new BonitaException("Operation specified is not supported.");
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    public BonitaConfiguration getConfiguration() {
        return configuration;
    }

}
