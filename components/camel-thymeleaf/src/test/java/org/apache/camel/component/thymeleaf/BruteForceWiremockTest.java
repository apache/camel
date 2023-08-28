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
package org.apache.camel.component.thymeleaf;

import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.UrlTemplateResolver;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WireMockTest(httpPort = 9843)
public class BruteForceWiremockTest {

    private static final String TEMPLATE_NAME = "letterWithUrl";

    private static final Logger LOG = LoggerFactory.getLogger(BruteForceWiremockTest.class);

    @Test
    public void testUrlTemplateResolver(WireMockRuntimeInfo wmRuntimeInfo) {

        stubFor(get("/dontcare.html").willReturn(ok(fragment())));

        TemplateEngine templateEngine = new TemplateEngine();

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/org/apache/camel/component/thymeleaf/");
        resolver.setSuffix(".html");
        resolver.setOrder(2);

        UrlTemplateResolver urlTemplateResolver = new UrlTemplateResolver();
        urlTemplateResolver.setCharacterEncoding("UTF-8");
        urlTemplateResolver.setOrder(1);

        templateEngine.addTemplateResolver(urlTemplateResolver);
        templateEngine.addTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result);

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    protected Context context() {

        Context context = new Context();
        context.setVariable("headers", headers());
        context.setVariable("body", body());

        return context;
    }

    protected Object body() {

        return "Spazz Testing Service";
    }

    protected Map<String, Object> headers() {

        Map<String, Object> headers = new HashMap<>();
        headers.put("lastName", "Doe");
        headers.put("firstName", "Jane");
        headers.put("item", "Widgets for Dummies");

        return headers;
    }

    protected String fragment() {

        return """
                <span th:fragment="test" th:remove="tag">
                You will be notified when your order ships.
                </span>
                """;
    }

}
