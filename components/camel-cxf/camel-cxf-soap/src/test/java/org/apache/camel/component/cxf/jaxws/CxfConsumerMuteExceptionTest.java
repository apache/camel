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
package org.apache.camel.component.cxf.jaxws;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.greeter_control.Greeter;
import org.apache.cxf.greeter_control.PingMeFault;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The consumer used to put the route exception's message into the SOAP fault returned to the caller, so any
 * remote-triggerable failure handed the caller internal detail.
 * <p>
 * {@code muteException} defaults to true, matching the http consumers aligned by CAMEL-23651. It applies only to
 * <em>undeclared</em> failures: an exception the service contract declares with {@code @WebFault} is what a SOAP client
 * is written against, so it is still returned in full.
 */
class CxfConsumerMuteExceptionTest extends CamelTestSupport {

    private static final String DETAIL = "the-internal-detail-a-caller-must-not-see";

    private static final String MUTED_ADDRESS
            = "http://localhost:" + CXFTestSupport.getPort1() + "/CxfConsumerMuteExceptionTest/muted";
    private static final String UNMUTED_ADDRESS
            = "http://localhost:" + CXFTestSupport.getPort2() + "/CxfConsumerMuteExceptionTest/unmuted";
    private static final String DECLARED_ADDRESS
            = "http://localhost:" + CXFTestSupport.getPort3() + "/CxfConsumerMuteExceptionTest/declared";

    private static final String SERVICE_CLASS = "serviceClass=org.apache.cxf.greeter_control.Greeter";

    @Test
    void anUndeclaredFailureIsNotDescribedToTheCaller() {
        assertThatThrownBy(() -> client(MUTED_ADDRESS).pingMe())
                .as("the SOAP fault must carry neither the class nor the message of the route's exception")
                .hasMessageNotContaining(DETAIL)
                .hasMessageNotContaining("IllegalStateException");
    }

    @Test
    void muteExceptionFalseDescribesTheFailureAsBefore() {
        assertThatThrownBy(() -> client(UNMUTED_ADDRESS).pingMe())
                .hasMessageContaining(DETAIL);
    }

    /**
     * The guarantee that makes muting safe to default on: a fault the WSDL declares is part of the contract, so muting
     * must not touch it. Without this carve-out every SOAP client written against a declared fault would break.
     */
    @Test
    void aDeclaredWebFaultIsStillReturnedInFullWhileMuted() {
        assertThatThrownBy(() -> client(DECLARED_ADDRESS).pingMe())
                .isInstanceOf(PingMeFault.class)
                .hasMessageContaining(DETAIL);
    }

    @Test
    void muteExceptionDefaultsToTrue() {
        CxfEndpoint endpoint = context.getEndpoint("cxf://" + MUTED_ADDRESS + "?" + SERVICE_CLASS, CxfEndpoint.class);
        assertThat(endpoint.isMuteException()).isTrue();
    }

    private static Greeter client(String address) {
        JaxWsProxyFactoryBean proxyFactory = new JaxWsProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(address);
        clientBean.setServiceClass(Greeter.class);
        return (Greeter) proxyFactory.create();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("cxf://" + MUTED_ADDRESS + "?" + SERVICE_CLASS)
                        .process(e -> {
                            throw new IllegalStateException(DETAIL);
                        });
                from("cxf://" + UNMUTED_ADDRESS + "?" + SERVICE_CLASS + "&muteException=false")
                        .process(e -> {
                            throw new IllegalStateException(DETAIL);
                        });
                from("cxf://" + DECLARED_ADDRESS + "?" + SERVICE_CLASS)
                        .process(e -> {
                            throw new PingMeFault(DETAIL);
                        });
            }
        };
    }
}
