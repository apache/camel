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
package org.apache.camel.itest.osgi.hdfs;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class HdfsBlueprintRouteTest extends OSGiBlueprintTestSupport {

    @Test
    public void testWriteAndReadString() throws Exception {
        if (isJavaVendor("IBM")) {
            // Hadoop doesn't run on IBM JDK
            return;
        }

        if (isPlatform("windows")) {
            // and does not work well on windows
            return;
        }

        getInstalledBundle("CamelBlueprintHdfsTestBundle").start();
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintHdfsTestBundle)", 20000);

        ProducerTemplate template = ctx.createProducerTemplate();
        template.sendBody("direct:start", "CIAO");

        MockEndpoint resultEndpoint = ctx.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.assertIsSatisfied();
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = combine(
                getDefaultCamelKarafOptions(),
                provision(TinyBundles.bundle()
                          .add("core-default.xml", HdfsRouteTest.class.getResource("/core-default.xml"))
                          .add("hdfs-default.xml", HdfsRouteTest.class.getResource("/hdfs-default.xml"))
                          .add("OSGI-INF/blueprint/test.xml", HdfsRouteTest.class.getResource("blueprintCamelContext.xml"))
                          .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintHdfsTestBundle")
                          .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                          .build()),

                // using the features to install the camel components
                loadCamelFeatures(
                        "camel-blueprint", "camel-hdfs2"));
                
        return options;
    }
}
