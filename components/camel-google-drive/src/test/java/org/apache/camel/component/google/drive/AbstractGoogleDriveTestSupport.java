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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.model.File;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IntrospectionSupport;

public abstract class AbstractGoogleDriveTestSupport extends CamelTestSupport {

    protected static final String CAMEL_TEST_TAG = "camel_was_here";
    protected static final String CAMEL_TEST_FILE = "CamelTestFile";
    protected static String testUserId;
    protected static String testFolderId;
    protected static String testFileId;

    protected static final String TEST_UPLOAD_FILE = "src/test/resources/log4j2.properties";
    protected static final String TEST_UPLOAD_IMG = "src/test/resources/camel-box-small.png";
    protected static final java.io.File UPLOAD_FILE = new java.io.File(TEST_UPLOAD_FILE);

    private static String refreshToken;
    private static String propertyText;

    
    private static final String TEST_OPTIONS_PROPERTIES = "/test-options.properties";
    private static final String REFRESH_TOKEN_PROPERTY = "refreshToken";
    
    
    protected File uploadTestFile() {
        File fileMetadata = new File();
        fileMetadata.setTitle(UPLOAD_FILE.getName());
        FileContent mediaContent = new FileContent(null, UPLOAD_FILE);
        
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is com.google.api.services.drive.model.File
        headers.put("CamelGoogleDrive.content", fileMetadata);
        // parameter type is com.google.api.client.http.AbstractInputStreamContent
        headers.put("CamelGoogleDrive.mediaContent", mediaContent);

        File result = requestBodyAndHeaders("google-drive://drive-files/insert", null, headers);
        return result;
    }

    protected File uploadTestFolder() {
        File fileMetadata = new File();
        fileMetadata.setTitle("testfolder");
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        File result = requestBody("google-drive://drive-files/insert?inBody=content", fileMetadata);
        return result;
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {

        final InputStream in = getClass().getResourceAsStream(TEST_OPTIONS_PROPERTIES);
        if (in == null) {
            throw new IOException(TEST_OPTIONS_PROPERTIES + " could not be found");
        }

        final StringBuilder builder = new StringBuilder();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append(LS);
        }
        propertyText = builder.toString();

        final Properties properties = new Properties();
        try {
            properties.load(new StringReader(propertyText));
        } catch (IOException e) {
            throw new IOException(String.format("%s could not be loaded: %s", TEST_OPTIONS_PROPERTIES, e.getMessage()),
                e);
        }
//
//        // cache test properties
//        refreshToken = properties.getProperty(REFRESH_TOKEN_PROPERTY);
//        testFolderId = properties.getProperty("testFolderId");
//        testFileId = properties.getProperty("testFileId");
//        testUserId = properties.getProperty("testUserId");
//
        Map<String, Object> options = new HashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            options.put(entry.getKey().toString(), entry.getValue());
        }

        final GoogleDriveConfiguration configuration = new GoogleDriveConfiguration();
        IntrospectionSupport.setProperties(configuration, options);

        // add GoogleDriveComponent  to Camel context
        final CamelContext context = super.createCamelContext();
        final GoogleDriveComponent component = new GoogleDriveComponent(context);

        component.setConfiguration(configuration);
        context.addComponent("google-drive", component);

        return context;
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

    protected <T> T requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers, Class<T> type)
        throws CamelExecutionException {
        return template().requestBodyAndHeaders(endpointUri, body, headers, type);
    }
    
    protected <T> T requestBody(String endpoint, Object body) throws CamelExecutionException {
        return (T) template().requestBody(endpoint, body);
    }
}
