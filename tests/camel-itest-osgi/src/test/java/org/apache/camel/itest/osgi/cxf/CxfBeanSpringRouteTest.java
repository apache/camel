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
package org.apache.camel.itest.osgi.cxf;

import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class CxfBeanSpringRouteTest extends OSGiIntegrationSpringTestSupport {

    @Test
    public void testGetCustomer() throws Exception {
        HttpGet get = new HttpGet("http://localhost:9000/route/customerservice/customers/123");
        get.addHeader("Accept" , "application/json");
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("{\"Customer\":{\"id\":123,\"name\":\"John\"}}",
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.close();
        }
    }

    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/cxf/CxfBeanRouter.xml"});
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
                getDefaultCamelKarafOptions(),

                // using the features to install the camel components
                loadCamelFeatures(
                        "camel-jetty", "camel-http4", "camel-cxf"),

                provision(TinyBundles.bundle()
                        .add(org.apache.camel.itest.osgi.cxf.jaxrs.testbean.Customer.class)
                        .add(org.apache.camel.itest.osgi.cxf.jaxrs.testbean.CustomerService.class)
                        .add(org.apache.camel.itest.osgi.cxf.jaxrs.testbean.CustomerServiceResource.class)
                        .add(org.apache.camel.itest.osgi.cxf.jaxrs.testbean.Order.class)
                        .add(org.apache.camel.itest.osgi.cxf.jaxrs.testbean.Product.class)
                        .set("Export-Package", "org.apache.camel.itest.osgi.cxf.jaxrs.testbean")
                        .build(TinyBundles.withBnd()))//,
                //vmOption("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5006")
        );

        return options;
    }
}
