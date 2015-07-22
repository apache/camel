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
package org.apache.camel.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Assert;
import org.junit.Test;

public class CamelSalesforceMojoIntegrationTest {

    private static final String TEST_LOGIN_PROPERTIES = "../test-salesforce-login.properties";

    @Test
    public void testExecute() throws Exception {
        CamelSalesforceMojo mojo = createMojo();

        // generate code
        mojo.execute();

        // validate generated code
        // check that it was generated
        Assert.assertTrue("Output directory was not created", mojo.outputDirectory.exists());

        // TODO check that the generated code compiles
    }

    protected CamelSalesforceMojo createMojo() throws IOException {
        CamelSalesforceMojo mojo = new CamelSalesforceMojo();

        mojo.setLog(new SystemStreamLog());

        // set login properties
        setLoginProperties(mojo);

        // set defaults
        mojo.version = System.getProperty("apiVersion", SalesforceEndpointConfig.DEFAULT_VERSION);
        mojo.loginUrl = System.getProperty("loginUrl", SalesforceLoginConfig.DEFAULT_LOGIN_URL);
        mojo.outputDirectory = new File("target/generated-sources/camel-salesforce");
        mojo.packageName = "org.apache.camel.salesforce.dto";

        // set code generation properties
        mojo.includePattern = "(.*__c)|(PushTopic)|(Document)|(Account)";

        // remove generated code directory
        if (mojo.outputDirectory.exists()) {
            // remove old files
            for (File file : mojo.outputDirectory.listFiles()) {
                file.delete();
            }
            mojo.outputDirectory.delete();
        }
        return mojo;
    }

    private void setLoginProperties(CamelSalesforceMojo mojo) throws IOException {
        // load test-salesforce-login properties
        Properties properties = new Properties();
        InputStream stream = null;
        try {
            stream = new FileInputStream(TEST_LOGIN_PROPERTIES);
            properties.load(stream);
            mojo.clientId = properties.getProperty("clientId");
            mojo.clientSecret = properties.getProperty("clientSecret");
            mojo.userName = properties.getProperty("userName");
            mojo.password = properties.getProperty("password");
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("Create a properties file named "
                    + TEST_LOGIN_PROPERTIES + " with clientId, clientSecret, userName, password"
                    + " for a Salesforce account with Merchandise and Invoice objects from Salesforce Guides.");
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignore) {
                    // noop
                }
            }
        }
    }

}
