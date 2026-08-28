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
package org.apache.camel.component.mina;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchangeHolder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DefaultExchangeHolder#unmarshal} rebuilds the whole Exchange from the payload - id, body, headers (via a
 * wholesale {@code setHeaders}), out message and every property. The helper used to do that for any decoded payload
 * that happened to be a holder, without checking whether the endpoint had asked for exchange transfer at all.
 * <p>
 * Reaching it from the wire additionally requires a widened {@code objectCodecPattern}, since the object codec's
 * accept-list refuses an arbitrary class by default - so this is defence in depth rather than a reachable hole. It
 * brings camel-mina in line with {@code JmsBinding}, which unmarshals a holder only on the opted-in path.
 */
class MinaExchangeHolderGateTest extends BaseMinaTest {

    private static final String FORGED = "forged-by-the-peer";

    private DefaultExchangeHolder holder() {
        Exchange source = createExchangeWithBody("holder-body");
        source.getIn().setHeader("CamelFileName", "../../" + FORGED);
        source.setProperty(FORGED, Boolean.TRUE);
        return (DefaultExchangeHolder) DefaultExchangeHolder.marshal(source);
    }

    @Test
    void aHolderIsNotUnmarshalledWhenTransferExchangeIsOff() {
        MinaEndpoint endpoint = context.getEndpoint(uri(""), MinaEndpoint.class);
        Exchange target = createExchangeWithBody("original");

        MinaPayloadHelper.setIn(endpoint, target, holder());

        assertThat(target.getIn().getHeader("CamelFileName"))
                .as("a decoded holder must not rebuild the exchange when transferExchange is off")
                .isNull();
        assertThat(target.getProperty(FORGED)).isNull();
        assertThat(target.getIn().getBody())
                .as("it is delivered as an ordinary body instead")
                .isInstanceOf(DefaultExchangeHolder.class);
    }

    @Test
    void aHolderIsStillUnmarshalledWhenTransferExchangeIsOn() {
        MinaEndpoint endpoint = context.getEndpoint(uri("&transferExchange=true"), MinaEndpoint.class);
        Exchange target = createExchangeWithBody("original");

        MinaPayloadHelper.setIn(endpoint, target, holder());

        assertThat(target.getIn().getBody()).isEqualTo("holder-body");
        assertThat(target.getIn().getHeader("CamelFileName")).isEqualTo("../../" + FORGED);
        assertThat(target.getProperty(FORGED)).isEqualTo(Boolean.TRUE);
    }

    /** setOut follows the same gate. */
    @Test
    void setOutFollowsTheSameGate() {
        MinaEndpoint off = context.getEndpoint(uri(""), MinaEndpoint.class);
        Exchange target = createExchangeWithBody("original");

        MinaPayloadHelper.setOut(off, target, holder());

        assertThat(target.getMessage().getHeader("CamelFileName")).isNull();
        assertThat(target.getMessage().getBody()).isInstanceOf(DefaultExchangeHolder.class);
    }

    private String uri(String extra) {
        return String.format("mina:tcp://localhost:%1$s?sync=false%2$s", getPort(), extra);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(uri("")).to("mock:result");
            }
        };
    }
}
