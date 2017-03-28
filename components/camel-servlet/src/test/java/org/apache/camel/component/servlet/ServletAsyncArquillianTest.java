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
package org.apache.camel.component.servlet;

import java.net.URL;
import java.nio.file.Paths;
import java.text.MessageFormat;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(Arquillian.class)
public class ServletAsyncArquillianTest {

    @Deployment
    public static Archive<?> createTestArchive() {
        // this is a WAR project so use WebArchive
        return ShrinkWrap.create(WebArchive.class)
            // add the web.xml
            .setWebXML(Paths.get("src/test/resources/org/apache/camel/component/servlet/web-spring-async.xml").toFile());
    }

    /**
     *
     * @param url the URL is the URL to the web application that was deployed
     * @throws Exception
     */
    @Test
    @RunAsClient
    public void testHello(@ArquillianResource URL url) throws Exception {
        final String name = "Arnaud";
        given().
            baseUri(url.toString()).
            queryParam("name", name).
        when().
            get("/services/hello").
        then().
            body(equalTo(MessageFormat.format("Hello {0} how are you?", name)));
    }
}
