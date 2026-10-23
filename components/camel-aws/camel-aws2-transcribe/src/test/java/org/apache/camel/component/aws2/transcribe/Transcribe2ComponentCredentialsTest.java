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
package org.apache.camel.component.aws2.transcribe;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class Transcribe2ComponentCredentialsTest extends CamelTestSupport {

    @Test
    public void defaultCredentialsProviderWithoutKeys() throws Exception {
        Transcribe2Component component = context.getComponent("aws2-transcribe", Transcribe2Component.class);
        Transcribe2Endpoint endpoint = (Transcribe2Endpoint) component
                .createEndpoint("aws2-transcribe://label?operation=listVocabularies&useDefaultCredentialsProvider=true");

        assertThat(endpoint.getConfiguration().isUseDefaultCredentialsProvider()).isTrue();
    }

    @Test
    public void profileCredentialsProviderWithoutKeys() throws Exception {
        Transcribe2Component component = context.getComponent("aws2-transcribe", Transcribe2Component.class);
        Transcribe2Endpoint endpoint = (Transcribe2Endpoint) component
                .createEndpoint("aws2-transcribe://label?operation=listVocabularies&useProfileCredentialsProvider=true");

        assertThat(endpoint.getConfiguration().isUseProfileCredentialsProvider()).isTrue();
    }

    @Test
    public void noCredentialsFails() {
        // the component takes the keys from the environment when the endpoint has none
        assumeTrue(System.getenv("AWS_ACCESS_KEY_ID") == null && System.getProperty("aws.accessKeyId") == null);
        Transcribe2Component component = context.getComponent("aws2-transcribe", Transcribe2Component.class);

        assertThatThrownBy(() -> component.createEndpoint("aws2-transcribe://label?operation=listVocabularies"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessKey and secretKey must be specified");
    }
}
