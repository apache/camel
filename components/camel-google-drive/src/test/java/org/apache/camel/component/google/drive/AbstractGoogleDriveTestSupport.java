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
package org.apache.camel.component.google.drive;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IntrospectionSupport;
import org.junit.AfterClass;

public abstract class AbstractGoogleDriveTestSupport extends CamelTestSupport {

    protected static final String CAMEL_TEST_TAG = "camel_was_here";
    protected static final String CAMEL_TEST_FILE = "CamelTestFile";
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final String TEST_OPTIONS_PROPERTIES = "/test-options.properties";
    private static final String REFRESH_TOKEN_PROPERTY = "refreshToken";
    protected static String testUserId;

    private static String refreshToken;
    private static String propertyText;

    protected static String testFolderId;
    protected static String testFileId;

    @Override
    protected CamelContext createCamelContext() throws Exception {

        final InputStream in = getClass().getResourceAsStream(TEST_OPTIONS_PROPERTIES);
        if (in == null) {
            throw new IOException(TEST_OPTIONS_PROPERTIES + " could not be found");
        }

        final StringBuilder builder = new StringBuilder();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while((line = reader.readLine()) != null) {
            builder.append(line).append(LINE_SEPARATOR);
        }
        propertyText = builder.toString();

        final Properties properties = new Properties();
        try {
            properties.load(new StringReader(propertyText));
        } catch (IOException e) {
            throw new IOException(String.format("%s could not be loaded: %s", TEST_OPTIONS_PROPERTIES, e.getMessage()),
                e);
        }

        // cache test properties
        refreshToken = properties.getProperty(REFRESH_TOKEN_PROPERTY);
        testFolderId = properties.getProperty("testFolderId");
        testFileId = properties.getProperty("testFileId");
        testUserId = properties.getProperty("testUserId");

        Map<String, Object> options = new HashMap<String, Object>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            options.put(entry.getKey().toString(), entry.getValue());
        }

        final GoogleDriveConfiguration configuration = new GoogleDriveConfiguration();
        IntrospectionSupport.setProperties(configuration, options);

        // add GoogleDriveComponent  to Camel context
        final CamelContext context = super.createCamelContext();
        final GoogleDriveComponent component = new GoogleDriveComponent(context);

        component.setConfiguration(configuration);
        context.addComponent("box", component);

        return context;
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        CamelTestSupport.tearDownAfterClass();
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        // only create the context once for this class
        return true;
    }

    protected <T> T requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers)
        throws CamelExecutionException {
        return (T) template().requestBodyAndHeaders(endpointUri, body, headers);
    }

    protected <T> T requestBody(String endpoint, Object body) throws CamelExecutionException {
        return (T) template().requestBody(endpoint, body);
    }
}
