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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.transcribe.TranscribeClient;

/**
 * Automatically convert speech to text using AWS Transcribe service
 */
@UriEndpoint(firstVersion = "4.15.0", scheme = "aws2-transcribe", title = "AWS Transcribe",
             syntax = "aws2-transcribe:label", category = { Category.CLOUD, Category.MESSAGING },
             producerOnly = true, headersClass = Transcribe2Constants.class)
public class Transcribe2Endpoint extends DefaultEndpoint {

    private TranscribeClient transcribeClient;

    @UriParam
    private Transcribe2Configuration configuration;

    public Transcribe2Endpoint(String uri, Component component, Transcribe2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Transcribe2Producer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        transcribeClient = ObjectHelper.isNotEmpty(configuration.getTranscribeClient())
                ? configuration.getTranscribeClient() : Transcribe2ClientFactory.getTranscribeClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getTranscribeClient())) {
            if (ObjectHelper.isNotEmpty(transcribeClient)) {
                transcribeClient.close();
            }
        }
        super.doStop();
    }

    public Transcribe2Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Transcribe2Configuration configuration) {
        this.configuration = configuration;
    }

    public TranscribeClient getTranscribeClient() {
        return transcribeClient;
    }

    public void setTranscribeClient(TranscribeClient transcribeClient) {
        this.transcribeClient = transcribeClient;
    }
}
