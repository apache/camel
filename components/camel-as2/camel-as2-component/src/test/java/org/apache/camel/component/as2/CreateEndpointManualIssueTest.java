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
package org.apache.camel.component.as2;

import java.nio.charset.Charset;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.internal.AS2ApiName;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A bad way to create the endpoint reported by Camel user.
 */
public class CreateEndpointManualIssueTest {

    @Test
    public void testCreateEndpoint() throws Exception {
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.start();

        org.apache.http.entity.ContentType contentTypeEdifact
                = org.apache.http.entity.ContentType.create("application/edifact", (Charset) null);

        String methodName = "send";
        AS2ApiName as2ApiNameClient = AS2ApiName.CLIENT;

        AS2Configuration endpointConfiguration = new AS2Configuration();
        endpointConfiguration.setApiName(as2ApiNameClient);
        endpointConfiguration.setMethodName(methodName);
        endpointConfiguration.setRequestUri("/as2/HttpReceiver");
        endpointConfiguration.setAs2MessageStructure(AS2MessageStructure.PLAIN);

        endpointConfiguration.setAs2Version("1.0");
        endpointConfiguration.setAs2To("mendelsontestAS2");
        endpointConfiguration.setAs2From("mycompanyAS2");
        endpointConfiguration.setEdiMessageType(contentTypeEdifact);
        endpointConfiguration.setFrom("dk2kEdi");
        endpointConfiguration.setSubject("mysubject");
        endpointConfiguration.setSigningAlgorithm(AS2SignatureAlgorithm.MD2WITHRSA);
        endpointConfiguration.setEdiMessageTransferEncoding("7bit");

        AS2Component as2Component = new AS2Component();
        as2Component.setCamelContext(camelContext);
        as2Component.setConfiguration(endpointConfiguration);
        as2Component.start();

        AS2Endpoint endpoint = (AS2Endpoint) as2Component
                .createEndpoint("as2://client/send?targetHostName=testas2.mendelson-e-c.com"
                                + "&targetPortNumber=8080&inBody=ediMessage&requestUri=/as2/HttpReceiver");

        Assertions.assertEquals("mycompanyAS2", endpoint.getAs2From());
        Assertions.assertEquals("mendelsontestAS2", endpoint.getAs2To());
        Assertions.assertEquals("dk2kEdi", endpoint.getFrom());

        // should fail but that there are some missing options, but not the ones we set on configuration
        Exchange out
                = camelContext.createProducerTemplate().request(endpoint, exchange -> exchange.getIn().setBody("Hello World"));
        Throwable cause = out.getException();
        Assertions.assertNotNull(cause);

        Assertions.assertTrue(cause.getMessage().contains("Missing properties for send, need one or more from (9 args)"));
        Assertions.assertFalse(cause.getMessage().contains("ediMessageType"));
    }
}
