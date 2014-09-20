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
package org.apache.camel.itest.osgi.velocity;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class VelocityBlueprintTest extends OSGiBlueprintTestSupport {
    private CamelContext camelContext;
    private ProducerTemplate mytemplate;
    
    @Test
    public void testReceivesResponse() throws Exception {        
        assertRespondsWith("foo", "<header>foo</header><hello>foo</hello>");
        assertRespondsWith("bar", "<header>bar</header><hello>bar</hello>");
    }

    protected void assertRespondsWith(final String value, String expectedBody) throws InvalidPayloadException {
        Exchange response = mytemplate.request("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                in.setBody("answer");
                in.setHeader("cheese", value);
            }
        });
        assertOutMessageBodyEquals(response, expectedBody);
    }
    
    protected void doPostSetup() throws Exception {
        getInstalledBundle("VelocityBlueprintRouterTest").start();
        camelContext = getOsgiService(CamelContext.class, "(camel.context.symbolicname=VelocityBlueprintRouterTest)", 10000);
        mytemplate = camelContext.createProducerTemplate();
        mytemplate.start();
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
                getDefaultCamelKarafOptions(),
                // using the features to install the camel components
                loadCamelFeatures(
                        "camel-blueprint", "camel-velocity"),

                bundle(TinyBundles.bundle()
                        .add("OSGI-INF/blueprint/test.xml", VelocityBlueprintTest.class.getResource("VelocityBlueprintRouter.xml"))
                        .add("example.vm", VelocityBlueprintTest.class.getResource("example.vm"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "VelocityBlueprintRouterTest")
                        .build(TinyBundles.withBnd())).noStart()

        );

        return options;
    }

}
