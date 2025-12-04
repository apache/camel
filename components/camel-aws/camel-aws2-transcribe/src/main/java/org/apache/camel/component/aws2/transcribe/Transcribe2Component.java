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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

@Component("aws2-transcribe")
public class Transcribe2Component extends DefaultComponent {

    /**
     * Component configuration
     */
    @Metadata
    private Transcribe2Configuration configuration = new Transcribe2Configuration();

    public Transcribe2Component() {
        this(null);
    }

    public Transcribe2Component(CamelContext context) {
        super(context);

        registerExtension(new Transcribe2ComponentVerifierExtension("aws2-transcribe"));
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Label must be specified.");
        }

        Transcribe2Configuration configuration =
                this.configuration != null ? this.configuration.clone() : new Transcribe2Configuration();
        configuration.setLabel(remaining);
        Transcribe2Endpoint endpoint = new Transcribe2Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getAccessKey())) {
            setAccessKeyOnEndpoint(endpoint);
        }
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getSecretKey())) {
            setSecretKeyOnEndpoint(endpoint);
        }
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getRegion())) {
            setRegionOnEndpoint(endpoint);
        }
        if (endpoint.getConfiguration().getTranscribeClient() == null
                && (endpoint.getConfiguration().getAccessKey() == null
                        || endpoint.getConfiguration().getSecretKey() == null)) {
            throw new IllegalArgumentException("Amazon transcribe client or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public Transcribe2Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Transcribe2Configuration configuration) {
        this.configuration = configuration;
    }

    private void setAccessKeyOnEndpoint(Transcribe2Endpoint endpoint) {
        String accessKey = System.getProperty("aws.accessKeyId");
        if (accessKey == null) {
            accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        }
        if (accessKey != null) {
            endpoint.getConfiguration().setAccessKey(accessKey);
        }
    }

    private void setSecretKeyOnEndpoint(Transcribe2Endpoint endpoint) {
        String secretKey = System.getProperty("aws.secretKey");
        if (secretKey == null) {
            secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        }
        if (secretKey != null) {
            endpoint.getConfiguration().setSecretKey(secretKey);
        }
    }

    private void setRegionOnEndpoint(Transcribe2Endpoint endpoint) {
        String region = System.getProperty("aws.region");
        if (region == null) {
            region = System.getenv("AWS_REGION");
        }
        if (region != null) {
            endpoint.getConfiguration().setRegion(region);
        }
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isNotEmpty(configuration) && ObjectHelper.isNotEmpty(configuration.getTranscribeClient())) {
            configuration.getTranscribeClient().close();
        }
        super.doStop();
    }
}
