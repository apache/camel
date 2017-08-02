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
import java.io.IOException;

import org.apache.camel.util.ObjectHelper;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.logging.SystemStreamLog;

class CamelServiceNowMojoTestSupport  {

    protected CamelServiceNowGenerateMojo createMojo() throws IOException {
        CamelServiceNowGenerateMojo mojo = new CamelServiceNowGenerateMojo();

        mojo.setLog(new SystemStreamLog());

        // set defaults
        mojo.instanceName = getSystemPropertyOrEnvVar("servicenow.instance");
        mojo.userName = getSystemPropertyOrEnvVar("servicenow.username");
        mojo.userPassword = getSystemPropertyOrEnvVar("servicenow.password");
        mojo.oauthClientId = getSystemPropertyOrEnvVar("servicenow.oauth2.client.id");
        mojo.oauthClientSecret = getSystemPropertyOrEnvVar("servicenow.oauth2.client.secret");
        mojo.outputDirectory = new File("target/generated-sources/camel-servicenow");
        mojo.packageName = "org.apache.camel.servicenow.dto";

        FileUtils.deleteDirectory(mojo.outputDirectory);

        return mojo;
    }

    public static String getSystemPropertyOrEnvVar(String systemProperty) {
        String answer = System.getProperty(systemProperty);
        if (ObjectHelper.isEmpty(answer)) {
            String envProperty = systemProperty.toUpperCase().replaceAll("[.-]", "_");
            answer = System.getenv(envProperty);
        }

        return answer;
    }
}
