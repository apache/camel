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
package org.apache.camel.itest.cxf;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.karaf.AbstractFeatureTest;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.message.Message;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.InnerClassStrategy;
import org.ops4j.pax.tinybundles.core.TinyBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

@RunWith(PaxExam.class)
@Ignore("Flaky on CI server")
public class CamelCxfBeanInjectTest extends AbstractFeatureTest {

    private static final int PORT = AvailablePortFinder.getNextAvailable(30000);
    private static final String ENDPOINT_ADDRESS = String.format(
        "http://localhost:%s/CamelCxfBeanInjectTest/router", PORT);

    @Before
    public void installBlueprintXML() throws Exception {
        // install the camel blueprint xml file we use in this test
        URL url = ObjectHelper.loadResourceAsURL("org/apache/camel/itest/cxf/CamelCxfBeanInjectTest.xml", CamelCxfBeanInjectTest.class.getClassLoader());
        Bundle bundle = installBlueprintAsBundle("CamelCxfBeanInjectTest", url, false, b -> {
            ((TinyBundle) b)
                .add(BeanInjectRouteBuilder.class, InnerClassStrategy.NONE)
                .add(SimpleService.class, InnerClassStrategy.NONE)
                .add(SimpleBean.class, InnerClassStrategy.NONE)
                .set(Constants.DYNAMICIMPORT_PACKAGE, "*");
        });

        Properties props = new Properties();
        props.put("router.address", ENDPOINT_ADDRESS);
        props.put("router.port", Integer.toString(PORT));
        overridePropertiesWithConfigAdmin("my-placeholders", props);

        bundle.start();
    }

    @Configuration
    public Option[] configure() {
        return configure("camel-test-karaf", "camel-cxf");
    }

    @Test
    public void testReverseProxy() {
        SimpleService client = createClient();
        setHttpHeaders(client, "X-Forwarded-Proto", "https");

        String result = client.op("test");
        Assert.assertEquals("Scheme should be set to 'https'",
            "scheme: https, x-forwarded-proto: https", result);
    }

    private void setHttpHeaders(SimpleService client, String header, String value) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(header, Arrays.asList(value));
        ClientProxy.getClient(client).getRequestContext().put(Message.PROTOCOL_HEADERS, headers);
    }

    private SimpleService createClient() {
        ClientProxyFactoryBean factory = new ClientProxyFactoryBean();
        factory.setAddress(ENDPOINT_ADDRESS);
        factory.setServiceClass(SimpleService.class);
        factory.setBus(BusFactory.getDefaultBus());
        return (SimpleService) factory.create();
    }
}
