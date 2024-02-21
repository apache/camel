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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.ComponentModel.ComponentOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EndpointHelperTest {

    @Test
    public void testSort1() throws IOException {
        final String json = PackageHelper.loadText(new File(
                Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component3.json")).getFile()));
        final ComponentModel componentModel = JsonMapper.generateComponentModel(json);

        componentModel.getComponentOptions().sort(EndpointHelper.createGroupAndLabelComparator());

        assertEquals("schemaRegistryURL,sslTrustmanagerAlgorithm,sslTruststoreLocation,sslTruststorePassword,"
                     + "sslTruststoreType,useGlobalSslContextParameters",
                componentModel.getComponentOptions().stream()
                        .map(ComponentOptionModel::getName).collect(Collectors.joining(",")));
    }

    @Test
    public void testSort2() throws IOException {
        final String json = PackageHelper.loadText(new File(
                Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component4.json")).getFile()));
        final ComponentModel componentModel = JsonMapper.generateComponentModel(json);

        componentModel.getComponentOptions().sort(EndpointHelper.createGroupAndLabelComparator());

        assertEquals("baseUri,clearHeaders,cryptoContextProperties,disallowDoctypeDecl,"
                     + "keySelector,omitXmlDeclaration,lazyStartProducer,outputNodeSearch,outputNodeSearchType,"
                     + "outputXmlEncoding,removeSignatureElements,schemaResourceUri,secureValidation,"
                     + "validationFailedHandler,xmlSignature2Message,xmlSignatureChecker,basicPropertyBinding,"
                     + "uriDereferencer,verifierConfiguration",
                componentModel.getComponentOptions().stream()
                        .map(ComponentOptionModel::getName).collect(Collectors.joining(",")));
    }

    @Test
    public void testRE() {
        Pattern copyRE = Pattern.compile("(\\[\\[.*)|(= .*)|(//.*)");
        Pattern attrRE = Pattern.compile(":[a-zA-Z0-9_-]*:( .*)?");

        String[] lines = {
                "[[any23-dataformat]]",
                "//= Any23 DataFormat",
                "= Any23 DataFormat"
        };
        Stream.of(lines).forEach(line -> {
            Matcher copy = copyRE.matcher(line);
            assertTrue(copy.matches(), line);
        });
        String[] attrlines = {
                ":attribute:",
                ":attribute: value",
                ":attri-bute: value",
                ":attri_bute: value",
                ":attribute: value",
                ":attribute: value \\"
        };
        Stream.of(attrlines).forEach(line -> {
            Matcher copy = attrRE.matcher(line);
            assertTrue(copy.matches(), line);
        });
    }
}
