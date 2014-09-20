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
package org.apache.camel.component.box;

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

import com.box.boxjavalibv2.authorization.IAuthSecureStorage;
import com.box.boxjavalibv2.authorization.OAuthRefreshListener;
import com.box.boxjavalibv2.dao.BoxOAuthToken;
import com.box.boxjavalibv2.dao.IAuthData;
import com.box.boxjavalibv2.requests.requestobjects.BoxPagingRequestObject;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.AfterClass;

public abstract class AbstractBoxTestSupport extends CamelTestSupport {

    protected static final String CAMEL_TEST_TAG = "camel_was_here";
    protected static final String CAMEL_TEST_FILE = "CamelTestFile";
    protected static final BoxPagingRequestObject BOX_PAGING_REQUEST_OBJECT = BoxPagingRequestObject.pagingRequestObject(100, 0);

    protected static String testUserId;
    protected static String testFolderId;
    protected static String testFileId;

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final String TEST_OPTIONS_PROPERTIES = "/test-options.properties";
    private static final String REFRESH_TOKEN_PROPERTY = "refreshToken";

    private static String refreshToken;
    private static String propertyText;

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
            builder.append(line).append(LINE_SEPARATOR);
        }
        propertyText = builder.toString();

        final Properties properties = new Properties();
        try {
            properties.load(new StringReader(propertyText));
        } catch (IOException e) {
            throw new IOException(String.format("%s could not be loaded: %s", TEST_OPTIONS_PROPERTIES, e.getMessage()), e);
        }

        addSystemProperty("camel.box.userName", "userName", properties);
        addSystemProperty("camel.box.userPassword", "userPassword", properties);
        addSystemProperty("camel.box.clientId", "clientId", properties);
        addSystemProperty("camel.box.clientSecret", "clientSecret", properties);
        addSystemProperty("camel.box.refreshToken", "refreshToken", properties);
        addSystemProperty("camel.box.testFolderId", "testFolderId", properties);
        addSystemProperty("camel.box.testFileId", "testFileId", properties);
        addSystemProperty("camel.box.testUserId", "testUserId", properties);

        // cache test properties
        refreshToken = properties.getProperty(REFRESH_TOKEN_PROPERTY);
        testFolderId = properties.getProperty("testFolderId");
        testFileId = properties.getProperty("testFileId");
        testUserId = properties.getProperty("testUserId");

        Map<String, Object> options = new HashMap<String, Object>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            options.put(entry.getKey().toString(), entry.getValue());
        }

        final BoxConfiguration configuration = new BoxConfiguration();
        IntrospectionSupport.setProperties(configuration, options);
        configuration.setAuthSecureStorage(new IAuthSecureStorage() {

            @Override
            public void saveAuth(IAuthData auth) {
                if (auth == null) {
                    // revoked
                    refreshToken = "";
                } else {
                    // remember the refresh token to write back to test-options.properties
                    refreshToken = auth.getRefreshToken();
                }
            }

            @Override
            public IAuthData getAuth() {
                if (ObjectHelper.isEmpty(refreshToken)) {
                    return null;
                } else {
                    Map<String, Object> values = new HashMap<String, Object>();
                    values.put(BoxOAuthToken.FIELD_REFRESH_TOKEN, refreshToken);
                    return new BoxOAuthToken(values);
                }
            }
        });
        configuration.setRefreshListener(new OAuthRefreshListener() {
            @Override
            public void onRefresh(IAuthData newAuthData) {
                log.debug("Refreshed OAuth data: " + ((newAuthData != null) ? newAuthData.getAccessToken() : null));
            }
        });

        // add BoxComponent to Camel context
        final CamelContext context = super.createCamelContext();
        final BoxComponent component = new BoxComponent(context);

        component.setConfiguration(configuration);
        context.addComponent("box", component);

        return context;
    }

    private void addSystemProperty(String sourceName, String targetName, Properties properties) {
        String value = System.getProperty(sourceName);
        if (value != null && !value.trim().isEmpty()) {
            properties.put(targetName, value);
        }
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        CamelTestSupport.tearDownAfterClass();

        // write the refresh token back to target/test-classes/test-options.properties
        final URL resource = AbstractBoxTestSupport.class.getResource(TEST_OPTIONS_PROPERTIES);
        final FileOutputStream out = new FileOutputStream(new File(resource.getPath()));
        propertyText = propertyText.replaceAll(REFRESH_TOKEN_PROPERTY + "=\\S*",
                REFRESH_TOKEN_PROPERTY + "=" + refreshToken);
        out.write(propertyText.getBytes("UTF-8"));
        out.close();
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        // only create the context once for this class
        return true;
    }

    protected <T> T requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) throws CamelExecutionException {
        return (T) template().requestBodyAndHeaders(endpointUri, body, headers);
    }

    protected <T> T requestBody(String endpoint, Object body) throws CamelExecutionException {
        return (T) template().requestBody(endpoint, body);
    }
}
