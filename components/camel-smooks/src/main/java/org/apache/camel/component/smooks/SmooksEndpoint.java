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
package org.apache.camel.component.smooks;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Service;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ProcessorEndpoint;
import org.smooks.api.SmooksException;

/**
 * Applies fragment-based processing in Smooks to perform a variety of operations like transformation.
 */
@UriEndpoint(firstVersion = "4.7.0", scheme = "smooks", title = "Smooks", syntax = "smooks://smooks-config-path", category = { Category.FILE })
public class SmooksEndpoint extends ProcessorEndpoint implements Service {

    @UriPath(description = "database name")
    @Metadata(required = true)
    private String database;

    private final SmooksProcessor smooksProcessor;

    public SmooksEndpoint(String endpointUri, Component component, SmooksProcessor processor) {
        super(endpointUri, component, processor);
        this.smooksProcessor = processor;
    }

    public void start() {
        try {
            smooksProcessor.start();
        } catch (Exception e) {
            throw new SmooksException(e.getMessage(), e);
        }
    }

    public void stop() {
        try {
            smooksProcessor.stop();
        } catch (Exception e) {
            throw new SmooksException(e.getMessage(), e);
        }
    }

}
