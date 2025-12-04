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

package org.apache.camel.component.tahu;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

/**
 * Sparkplug B Host Application support over MQTT using Eclipse Tahu
 */
@UriEndpoint(
        firstVersion = "4.8.0",
        scheme = TahuConstants.HOST_APP_SCHEME,
        title = "Tahu Host Application",
        syntax = TahuConstants.HOST_APP_ENDPOINT_URI_SYNTAX,
        consumerOnly = true,
        category = {Category.MESSAGING, Category.IOT, Category.MONITORING},
        headersClass = TahuConstants.class)
public class TahuHostEndpoint extends TahuDefaultEndpoint {

    @UriPath(label = "consumer", description = "ID for the host application")
    @Metadata(applicableFor = TahuConstants.HOST_APP_SCHEME, required = true)
    private final String hostId;

    TahuHostEndpoint(String uri, TahuHostComponent component, TahuConfiguration configuration, String hostId) {
        super(uri, component, configuration);

        this.hostId = hostId;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        TahuHostConsumer consumer =
                new TahuHostConsumer(this, processor, ObjectHelper.notNullOrEmpty(hostId, "hostId"));
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Cannot produce from this endpoint");
    }

    public String getHostId() {
        return hostId;
    }
}
