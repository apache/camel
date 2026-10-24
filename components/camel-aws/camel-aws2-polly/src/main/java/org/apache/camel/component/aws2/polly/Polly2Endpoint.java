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
package org.apache.camel.component.aws2.polly;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.polly.client.Polly2ClientFactory;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.polly.PollyClient;

/**
 * Synthesize speech using AWS Polly and AWS SDK version 2.x.
 */
@UriEndpoint(firstVersion = "4.18.0", scheme = "aws2-polly", title = "AWS Polly", syntax = "aws2-polly:label",
             producerOnly = true, category = { Category.CLOUD, Category.AI }, headersClass = Polly2Constants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "synthesizeSpeech",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }),
                                          @BodyType.When(option = "operation", value = "startSpeechSynthesisTask",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }) },
                                  description = "The text to speak for synthesizeSpeech, the default, and"
                                                + " startSpeechSynthesisTask: plain text, or SSML with textType=ssml. Bytes, a"
                                                + " stream or a file are read as text, a Map or a POJO is spoken as its"
                                                + " toString(), so take the text out of it first, and no body fails. putLexicon"
                                                + " takes the lexicon from the body when neither the lexiconContent option nor"
                                                + " the CamelAwsPollyLexiconContent header is set. The other operations do not"
                                                + " use the body. With pojoRequest=true the request of the AWS SDK for the"
                                                + " operation, such as a SynthesizeSpeechRequest: another body fails"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "synthesizeSpeech",
                                                          carriers = Carrier.STREAM),
                                           @BodyType.When(option = "operation", value = "describeVoices",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<software.amazon.awssdk.services.polly.model.Voice>"),
                                           @BodyType.When(option = "operation", value = "listLexicons", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<software.amazon.awssdk.services.polly.model.LexiconDescription>"),
                                           @BodyType.When(option = "operation", value = "listSpeechSynthesisTasks",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<software.amazon.awssdk.services.polly.model.SynthesisTask>") },
                                   description = "The audio of synthesizeSpeech, the default, as a stream, with its content"
                                                 + " type and the number of characters in headers: read it once, or write it to"
                                                 + " a file. A List for describeVoices, listLexicons and"
                                                 + " listSpeechSynthesisTasks, the SynthesisTask for startSpeechSynthesisTask,"
                                                 + " with its id in a header, and getSpeechSynthesisTask, the Lexicon for"
                                                 + " getLexicon, and the response of the AWS SDK for putLexicon and"
                                                 + " deleteLexicon"))
public class Polly2Endpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private PollyClient pollyClient;

    @UriParam
    private Polly2Configuration configuration;

    public Polly2Endpoint(String uri, Component component, Polly2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Polly2Producer(this);
    }

    @Override
    public Polly2Component getComponent() {
        return (Polly2Component) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        pollyClient
                = ObjectHelper.isNotEmpty(configuration.getPollyClient())
                        ? configuration.getPollyClient()
                        : Polly2ClientFactory.getPollyClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getPollyClient())) {
            if (ObjectHelper.isNotEmpty(pollyClient)) {
                pollyClient.close();
            }
        }
        super.doStop();
    }

    public Polly2Configuration getConfiguration() {
        return configuration;
    }

    public PollyClient getPollyClient() {
        return pollyClient;
    }

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                return configuration.getRegion();
            }
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "polly";
    }
}
