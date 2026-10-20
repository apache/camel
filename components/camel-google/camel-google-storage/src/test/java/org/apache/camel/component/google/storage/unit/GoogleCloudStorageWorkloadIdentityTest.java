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
package org.apache.camel.component.google.storage.unit;

import com.google.auth.Credentials;
import com.google.auth.oauth2.ExternalAccountCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import org.apache.camel.component.google.common.GoogleCredentialsHelper;
import org.apache.camel.component.google.storage.GoogleCloudStorageComponent;
import org.apache.camel.component.google.storage.GoogleCloudStorageConfiguration;
import org.apache.camel.component.google.storage.GoogleCloudStorageEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Workload Identity Federation options of the endpoint reach the credentials of the Storage client.
 */
class GoogleCloudStorageWorkloadIdentityTest extends CamelTestSupport {

    private static final String SERVICE_ACCOUNT = "storage-writer@my-project.iam.gserviceaccount.com";

    @Test
    void endpointBindsTheWorkloadIdentityOptions() throws Exception {
        GoogleCloudStorageConfiguration configuration = configuration(
                "google-storage://myCamelBucket?useWorkloadIdentityFederation=true"
                                                                      + "&workloadIdentityConfig=classpath:wif-config.json"
                                                                      + "&impersonatedServiceAccount=" + SERVICE_ACCOUNT);

        assertThat(configuration.isUseWorkloadIdentityFederation()).isTrue();
        assertThat(configuration.getWorkloadIdentityConfig()).isEqualTo("classpath:wif-config.json");
        assertThat(configuration.getImpersonatedServiceAccount()).isEqualTo(SERVICE_ACCOUNT);
    }

    @Test
    void credentialsComeFromTheWorkloadIdentityConfig() throws Exception {
        Credentials credentials = GoogleCredentialsHelper.getCredentials(context, configuration(
                "google-storage://myCamelBucket?useWorkloadIdentityFederation=true"
                                                                                                + "&workloadIdentityConfig=classpath:wif-config.json"));

        assertThat(credentials).isInstanceOf(ExternalAccountCredentials.class);
        assertThat(((ExternalAccountCredentials) credentials).getAudience()).endsWith("/providers/camel-provider");
    }

    @Test
    void credentialsImpersonateTheServiceAccount() throws Exception {
        Credentials credentials = GoogleCredentialsHelper.getCredentials(context, configuration(
                "google-storage://myCamelBucket?useWorkloadIdentityFederation=true"
                                                                                                + "&workloadIdentityConfig=classpath:wif-config.json"
                                                                                                + "&impersonatedServiceAccount="
                                                                                                + SERVICE_ACCOUNT));

        assertThat(credentials).isInstanceOf(ImpersonatedCredentials.class);
        ImpersonatedCredentials impersonated = (ImpersonatedCredentials) credentials;
        assertThat(impersonated.getAccount()).isEqualTo(SERVICE_ACCOUNT);
        assertThat(impersonated.getSourceCredentials()).isInstanceOf(ExternalAccountCredentials.class);
    }

    private GoogleCloudStorageConfiguration configuration(String uri) throws Exception {
        // not started: the endpoint would create its Storage client
        GoogleCloudStorageComponent component = context.getComponent("google-storage", GoogleCloudStorageComponent.class);
        return ((GoogleCloudStorageEndpoint) component.createEndpoint(uri)).getConfiguration();
    }
}
