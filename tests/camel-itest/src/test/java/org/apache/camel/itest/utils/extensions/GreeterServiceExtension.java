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

package org.apache.camel.itest.utils.extensions;

import jakarta.xml.ws.Endpoint;

import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GreeterServiceExtension implements BeforeAllCallback, AfterAllCallback {
    private static final Logger LOG = LoggerFactory.getLogger(GreeterServiceExtension.class);

    private static final GreeterImpl GREETER;
    private static final String ADDRESS;
    private static final int PORT;

    static {
        PORT = AvailablePortFinder.getNextAvailable();
        GREETER = new GreeterImpl();

        ADDRESS = "http://localhost:" + PORT + "/SoapContext/SoapPort";
        Endpoint.publish(ADDRESS, GREETER);
    }

    public GreeterServiceExtension(String portProperty) {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty(portProperty, Integer.toString(PORT));
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {

    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        LOG.info("The WS endpoint is published! ");
    }

    public static GreeterServiceExtension createExtension(String portProperty) {
        return new GreeterServiceExtension(portProperty);
    }

    public GreeterImpl getGreeter() {
        return GREETER;
    }
}
