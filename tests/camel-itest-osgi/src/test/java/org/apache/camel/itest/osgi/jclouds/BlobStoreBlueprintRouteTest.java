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

package org.apache.camel.itest.osgi.jclouds;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jclouds.JcloudsConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
// import org.jclouds.blobstore.BlobStoreContextFactory;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Constants;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.CoreOptions.workingDirectory;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;

@RunWith(JUnit4TestRunner.class)
@Ignore("See CAMEL-6536")
public class BlobStoreBlueprintRouteTest extends OSGiBlueprintTestSupport {

    private static final String TEST_CONTAINER = "testContainer";

    /**
     * Strategy to perform any pre setup, before {@link org.apache.camel.CamelContext} is created
     */
    @Override
    protected void doPreSetup() throws Exception {
/*        BlobStoreContextFactory contextFactory = new BlobStoreContextFactory();
        BlobStoreContext blobStoreContext = contextFactory.createContext("transient", "identity", "credential");
        BlobStore blobStore = blobStoreContext.getBlobStore();
        blobStore.createContainerInLocation(null, TEST_CONTAINER);
        blobStore.clearContainer(TEST_CONTAINER);
*/
    }

    @Test
    public void testProducerAndConsumer() throws Exception {
        getInstalledBundle("CamelBlueprintJcloudsTestBundle").start();
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintJcloudsTestBundle)", 20000);

        MockEndpoint mock = ctx.getEndpoint("mock:results", MockEndpoint.class);
        ProducerTemplate template = ctx.createProducerTemplate();
        mock.expectedMessageCount(2);

        template.sendBodyAndHeader("direct:start", "Test 1", JcloudsConstants.BLOB_NAME, "blob1");
        template.sendBodyAndHeader("direct:start", "Test 2", JcloudsConstants.BLOB_NAME, "blob2");

        assertMockEndpointsSatisfied();

        template.stop();
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
                getDefaultCamelKarafOptions(),
                //Helper.setLogLevel("INFO"),
                provision(newBundle()
                        .add("META-INF/persistence.xml", BlobStoreBlueprintRouteTest.class.getResource("/META-INF/persistence.xml"))
                        .add("OSGI-INF/blueprint/test.xml", BlobStoreBlueprintRouteTest.class.getResource("blueprintCamelContext.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintJcloudsTestBundle")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .set("Meta-Persistence", "META-INF/persistence.xml")
                        .build()),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", BlobStoreBlueprintRouteTest.class.getResource("blueprintBlobStoreService.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "org.apache.camel.jclouds.blobstore.service")
                        .set(Constants.BUNDLE_VERSION, "1.0.0")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).start(),

                // using the features to install the camel components
                loadCamelFeatures(
                        "camel-blueprint", "camel-jclouds"),
                workingDirectory("target/paxrunner/"),
                felix());

        // TODO: equinox fails for some reason

        return options;
    }
}