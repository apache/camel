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
package org.apache.camel.itest.osgi.blueprint;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;
import org.osgi.service.blueprint.container.BlueprintContainer;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class CamelMultiVersionBlueprintTest extends OSGiBlueprintTestSupport {

    @Test
    public void testFileRoute() throws Exception {

        getInstalledBundle("CamelBlueprintTestBundle1").start();
        getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle1)", 10000);
        CamelContext camelContext = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle1)", 10000);

        // delete the data directory
        deleteDirectory("target/data");

        MockEndpoint mock = camelContext.getEndpoint("mock:result", MockEndpoint.class);

        mock.expectedBodiesReceived("Hello World");
        // should be moved to .camel when done
        mock.expectedFileExists("target/data/.camel/hello.txt");

        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();

        producerTemplate.start();

        producerTemplate.sendBodyAndHeader("file:target/data", "Hello World", Exchange.FILE_NAME, "hello.txt");

        producerTemplate.stop();

        mock.assertIsSatisfied();
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = combine(
                getDefaultCamelKarafOptions(),

                scanFeatures(getCamelKarafFeatureUrl("2.8.0"),
                                "camel-core"),

                bundle(TinyBundles.bundle()
                        .add("OSGI-INF/blueprint/fileRoute.xml", OSGiBlueprintTestSupport.class.getResource("fileRouteBlueprint.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle1")
                                .build()).noStart(),

                // using the features to install the camel components
                loadCamelFeatures("camel-blueprint", "camel-test")

        );
        return options;
    }
}
