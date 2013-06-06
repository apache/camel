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
package org.apache.camel.component.spring.ws.addressing;

import java.net.URI;

import org.fest.assertions.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.ws.soap.addressing.client.ActionCallback;
import org.springframework.ws.soap.client.SoapFaultClientException;

/**
 * Provides abstract test for fault and output params for spring-ws:to: and
 * spring-ws:action: endpoints
 */
public abstract class AbstractConsumerTests extends AbstractWSATests {

    @Test
    public void defaultAction4ouput() throws Exception {
        ActionCallback requestCallback = channelIn("http://default-ok.com/");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getAction()).isEqualTo(getDefaultResponseAction());
    }

    @Test
    public void defaultAction4fault() throws Exception {
        ActionCallback requestCallback = channelIn("http://default-fault.com/");
        try {
            webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        } catch (SoapFaultClientException e) {
            // ok - cause fault response
        }
        Assertions.assertThat(channelOut().getAction()).isEqualTo(getDefaultResponseAction());
    }

    @Test
    public void customAction4output() throws Exception {
        ActionCallback requestCallback = channelIn("http://uri-ok.com");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://customURIOutputAction"));
    }

    @Test
    public void customAction4fault() throws Exception {
        ActionCallback requestCallback = channelIn("http://uri-fault.com");
        try {
            webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        } catch (SoapFaultClientException e) {
            // ok - cause fault response
        }
        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://customURIFaultAction"));
    }

    @Test
    @Ignore(value = "Not implemented yet")
    public void overrideHeaderAction4output() throws Exception {
        ActionCallback requestCallback = channelIn("http://override-ok.com");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://outputHeader.com"));
    }

    @Test
    @Ignore(value = "Not implemented yet")
    public void overrideHeaderAction4fault() throws Exception {
        ActionCallback requestCallback = channelIn("http://override-fault.com");
        try {
            webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        } catch (SoapFaultClientException e) {
            // ok - cause fault response
        }
        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://faultHeader.com"));
    }

    @Test
    @Ignore(value = "Not implemented yet")
    public void headerAction4output() throws Exception {
        ActionCallback requestCallback = channelIn("http://headerOnly-ok.com");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://outputHeader.com"));
    }

    @Test
    @Ignore(value = "Not implemented yet")
    public void headerAction4fault() throws Exception {
        ActionCallback requestCallback = channelIn("http://headerOnly-fault.com");
        try {
            webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        } catch (SoapFaultClientException e) {
            // ok - cause fault response
        }
        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://faultHeader.com"));
    }

    @Test
    public void onlyCustomOutputSpecified4output() throws Exception {
        ActionCallback requestCallback = channelIn("http://uriOutputOnly-ok.com/");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://customURIOutputAction"));
    }

    @Test
    public void onlyCustomOutputSpecified4fault() throws Exception {
        ActionCallback requestCallback = channelIn("http://uriOutputOnly-fault.com/");
        try {
            webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        } catch (SoapFaultClientException e) {
            // ok - cause fault response
        }
        Assertions.assertThat(channelOut().getAction()).isEqualTo(getDefaultResponseAction());
    }

    @Test
    public void onlyCustomFaultSpecified4output() throws Exception {
        ActionCallback requestCallback = channelIn("http://uriFaultOnly-ok.com/");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getAction()).isEqualTo(getDefaultResponseAction());
    }

    @Test
    public void onlyCustomFaultSpecified4fault() throws Exception {
        ActionCallback requestCallback = channelIn("http://uriFaultOnly-fault.com/");
        try {
            webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        } catch (SoapFaultClientException e) {
            // ok - cause fault response
        }
        Assertions.assertThat(channelOut().getAction()).isEqualTo(new URI("http://customURIFaultAction"));
    }

}
