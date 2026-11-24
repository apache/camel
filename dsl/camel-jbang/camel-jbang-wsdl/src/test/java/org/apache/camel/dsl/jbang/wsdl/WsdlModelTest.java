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
package org.apache.camel.dsl.jbang.wsdl;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Operation;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.Port;
import org.apache.camel.dsl.jbang.wsdl.WsdlModel.WsdlException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WsdlModelTest {

    private static final String ORDERS = "src/test/resources/wsdl/orders.wsdl";

    @Test
    void parsesTheContentOfAWsdl() throws Exception {
        WsdlModel model = WsdlModel.parse(Files.readAllBytes(Paths.get(ORDERS)));

        assertThat(model.targetNamespace()).isEqualTo("http://example.com/orders");
        Port port = model.services().get(0).ports().get(0);
        assertThat(port.name()).isEqualTo("OrderPort");
        assertThat(port.operations()).extracting(Operation::name).containsExactly("getOrder", "placeOrder");
        assertThat(model.warnings()).isEmpty();
    }

    @Test
    void theContentOfAWsdlFollowsNoImport() throws Exception {
        // the services of the WSDL itself, and two imports that would read a file and call another host
        byte[] wsdl = Files.readString(Paths.get(ORDERS)).replace("<wsdl:types>", """
                <wsdl:import namespace="urn:y" location="file:///etc/hosts"/>
                <wsdl:import namespace="urn:z" location="http://169.254.169.254/latest/meta-data"/>
                <wsdl:types>""").getBytes(StandardCharsets.UTF_8);

        WsdlModel model = WsdlModel.parse(wsdl);

        assertThat(model.services()).hasSize(1);
        assertThat(model.warnings()).hasSize(2)
                .allSatisfy(w -> assertThat(w).contains("is not followed, as the WSDL is given as content"));
    }

    @Test
    void theContentMustBeAWsdl() {
        assertThatThrownBy(() -> WsdlModel.parse(new byte[0])).isInstanceOf(WsdlException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> WsdlModel.parse("<foo/>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(WsdlException.class).hasMessageContaining("Not a WSDL 1.1 document");
        assertThatThrownBy(() -> WsdlModel.parse(
                "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/hosts\">]><x>&e;</x>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(WsdlException.class);
    }

    @Test
    void aRemoteWsdlImportsFromItsHostOverHttpOrHttps() throws Exception {
        URI http = URI.create("http://example.com/svc?wsdl");
        URI https = URI.create("https://example.com/svc?wsdl");
        URI file = URI.create("file:///tmp/svc.wsdl");

        // the same host, also on another port, and from http up to https
        assertThat(WsdlModel.refusedImport(http, http, URI.create("http://example.com/types.wsdl"))).isNull();
        assertThat(WsdlModel.refusedImport(http, http, URI.create("http://EXAMPLE.com:8080/types.wsdl"))).isNull();
        assertThat(WsdlModel.refusedImport(http, http, URI.create("https://example.com/types.wsdl"))).isNull();
        assertThat(WsdlModel.refusedImport(https, https, URI.create("https://example.com/types.wsdl"))).isNull();
        assertThat(WsdlModel.refusedImport(file, file, URI.create("file:///tmp/types.wsdl"))).isNull();

        // not from https down to http, not to another host, and not across files and http(s)
        assertThat(WsdlModel.refusedImport(https, https, URI.create("http://example.com/types.wsdl")))
                .isEqualTo("a WSDL read over https does not import over http");
        assertThat(WsdlModel.refusedImport(http, URI.create("https://example.com/types.wsdl"),
                URI.create("http://example.com/more.wsdl")))
                .isEqualTo("a WSDL read over https does not import over http");
        assertThat(WsdlModel.refusedImport(http, http, URI.create("http://169.254.169.254/latest/meta-data")))
                .startsWith("it is on the host 169.254.169.254, not on the host of the WSDL (example.com)");
        assertThat(WsdlModel.refusedImport(http, http, file)).isEqualTo("a remote WSDL only imports over http(s), not file");
        assertThat(WsdlModel.refusedImport(https, https, file))
                .isEqualTo("a remote WSDL only imports over http(s), not file");
        assertThat(WsdlModel.refusedImport(file, file, http)).isEqualTo("a WSDL file only imports files, not http");
        assertThat(WsdlModel.refusedImport(file, file, https)).isEqualTo("a WSDL file only imports files, not https");
    }

}
