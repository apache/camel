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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.nio.file.Files;
import java.nio.file.Paths;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.mcp.WsdlTools.InspectResult;
import org.apache.camel.dsl.jbang.core.commands.mcp.WsdlTools.OperationInfo;
import org.apache.camel.dsl.jbang.core.commands.mcp.WsdlTools.PortInfo;
import org.apache.camel.dsl.jbang.core.commands.mcp.WsdlTools.SampleInfo;
import org.apache.camel.dsl.jbang.core.commands.mcp.WsdlTools.ScaffoldResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WsdlToolsTest {

    private final WsdlTools tools = new WsdlTools();

    private static String orders() throws Exception {
        return Files.readString(Paths.get("src/test/resources/wsdl/orders.wsdl"));
    }

    @Test
    void inspectListsThePortsAndOperations() throws Exception {
        InspectResult result = tools.camel_wsdl_inspect(orders(), null, null);

        assertThat(result.targetNamespace()).isEqualTo("http://example.com/orders");
        PortInfo port = result.ports().get(0);
        assertThat(port.service()).isEqualTo("OrderService");
        assertThat(port.soapVersion()).isEqualTo("1.1");
        assertThat(port.cxf()).containsEntry("serviceName", "{http://example.com/orders}OrderService")
                .containsEntry("address", "http://localhost:18081/orders");
        assertThat(port.operations()).extracting(OperationInfo::name).containsExactly("getOrder", "placeOrder");
        OperationInfo getOrder = port.operations().get(0);
        assertThat(getOrder.input()).isEqualTo("{http://example.com/orders}getOrder");
        assertThat(getOrder.faults()).extracting(WsdlTools.FaultInfo::name).containsExactly("orderNotFound");
        // no samples unless asked for
        assertThat(getOrder.samples()).isNull();
    }

    @Test
    void inspectWithSamples() throws Exception {
        InspectResult result = tools.camel_wsdl_inspect(orders(), "getOrder", true);

        OperationInfo getOrder = result.ports().get(0).operations().get(0);
        assertThat(result.ports().get(0).operations()).hasSize(1);
        assertThat(getOrder.samples()).extracting(SampleInfo::kind).containsExactly("request", "response", "fault");
        SampleInfo request = getOrder.samples().get(0);
        assertThat(request.xml()).contains("<tns:getOrder xmlns:tns=\"http://example.com/orders\">");
        assertThat(request.json()).contains("\"orderId\"");
        assertThat(request.soap()).contains("<soapenv:Body>");
        assertThat(request.curl()).contains("SOAPAction: \"http://example.com/orders/getOrder\"");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void theImportsOfTheWsdlAreNotFollowed() throws Exception {
        String wsdl = orders().replace("<wsdl:types>", """
                <wsdl:import namespace="urn:y" location="file:///etc/hosts"/>
                <wsdl:types>
                  <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:t">
                    <xs:import namespace="urn:t2" schemaLocation="types.xsd"/>
                  </xs:schema>""");

        InspectResult result = tools.camel_wsdl_inspect(wsdl, null, true);

        assertThat(result.ports()).hasSize(1);
        assertThat(String.join("\n", result.warnings())).contains("is not followed, as the WSDL is given as content")
                .contains("Cannot load content:types.xsd");
    }

    @Test
    void scaffoldAConsumer() throws Exception {
        ScaffoldResult result = tools.camel_wsdl_scaffold(orders(), "consumer", "classpath:orders.wsdl", null, null, null);

        assertThat(result.yaml()).contains("uri: cxf:/orders", "wsdlURL: classpath:orders.wsdl",
                "${header.CamelCxfOperationName} == 'getOrder'");
        assertThat(result.port()).isEqualTo("OrderPort");
        assertThat(result.explanation()).contains("/services");
    }

    @Test
    void scaffoldAProducerOfOneOperation() throws Exception {
        ScaffoldResult result = tools.camel_wsdl_scaffold(orders(), "producer", null, null, null, "placeOrder");

        assertThat(result.yaml()).contains("uri: direct:placeOrder", "uri: cxf://http://localhost:18081/orders",
                "wsdlURL: classpath:service.wsdl").doesNotContain("direct:getOrder");
    }

    @Test
    void invalidArguments() throws Exception {
        String wsdl = orders();
        assertThatThrownBy(() -> tools.camel_wsdl_scaffold(wsdl, "both", null, null, null, null))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("'route' must be");
        assertThatThrownBy(() -> tools.camel_wsdl_scaffold(wsdl, "consumer", null, null, null, "nope"))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("Unknown operation: nope");
        assertThatThrownBy(() -> tools.camel_wsdl_scaffold(wsdl, "consumer", null, "Nope", null, null))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("No port matches");
        assertThatThrownBy(() -> tools.camel_wsdl_inspect(" ", null, null))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("required");
        assertThatThrownBy(() -> tools.camel_wsdl_inspect("<foo/>", null, null))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("Not a WSDL 1.1 document");
    }
}
