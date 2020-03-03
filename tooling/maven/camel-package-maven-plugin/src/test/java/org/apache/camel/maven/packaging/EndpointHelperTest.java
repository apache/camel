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
import java.util.stream.Collectors;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.ComponentModel.ComponentOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EndpointHelperTest {

    @Test
    public void testSort1() throws IOException {
        final String json = PackageHelper.loadText(new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component3.json")).getFile()));
        final ComponentModel componentModel = JsonMapper.generateComponentModel(json);

        componentModel.getComponentOptions().sort(EndpointHelper.createGroupAndLabelComparator());

        assertEquals("schemaRegistryURL,sslTrustmanagerAlgorithm,sslTruststoreLocation,sslTruststorePassword,"
                        + "sslTruststoreType,useGlobalSslContextParameters",
                componentModel.getComponentOptions().stream()
                    .map(ComponentOptionModel::getName).collect(Collectors.joining(",")));
    }

    @Test
    public void testSort2() throws IOException {
        final String json = PackageHelper.loadText(new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component4.json")).getFile()));
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

}
