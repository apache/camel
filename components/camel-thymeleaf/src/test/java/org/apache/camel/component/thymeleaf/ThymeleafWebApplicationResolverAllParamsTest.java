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

import java.io.File;
import java.io.InputStream;
import java.net.URL;

import jakarta.servlet.ServletContext;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

public class ThymeleafWebApplicationResolverAllParamsTest extends ThymeleafAbstractBaseTest {

    private static final String TEST_ENDPOINT = "testEndpoint";

    private ServletContext servletContext;

    private JakartaServletWebApplication jakartaServletWebApplication;

    @BeforeEach
    public void setUp() throws Exception {

        super.setUp();

        File initialFile = new File("src/test/resources/WEB-INF/templates/letter.html");
        InputStream targetStream = FileUtils.openInputStream(initialFile);
        Mockito.when(servletContext.getResourceAsStream(anyString())).thenReturn(targetStream);
        Mockito.when(servletContext.getResource(anyString())).thenReturn(new URL("http://localhost/letter.html"));
    }

    @Test
    public void testThymeleaf() throws InterruptedException {

        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(1);
        mock.message(0).body().contains(THANK_YOU_FOR_YOUR_ORDER);
        mock.message(0).body().endsWith(SPAZZ_TESTING_SERVICE);
        mock.message(0).header(ThymeleafConstants.THYMELEAF_TEMPLATE).isNull();
        mock.message(0).header(ThymeleafConstants.THYMELEAF_VARIABLE_MAP).isNull();
        mock.message(0).header(FIRST_NAME).isEqualTo(JANE);

        template.request(DIRECT_START, basicHeaderProcessor);

        mock.assertIsSatisfied();

        ThymeleafEndpoint thymeleafEndpoint = context.getEndpoint(TEST_ENDPOINT, ThymeleafEndpoint.class);

        assertAll("properties",
                () -> assertNotNull(thymeleafEndpoint),
                () -> assertTrue(thymeleafEndpoint.isAllowContextMapAll()),
                () -> assertTrue(thymeleafEndpoint.getCacheable()),
                () -> assertEquals(CACHE_TIME_TO_LIVE, thymeleafEndpoint.getCacheTimeToLive()),
                () -> assertTrue(thymeleafEndpoint.getCheckExistence()),
                () -> assertEquals(UTF_8_ENCODING, thymeleafEndpoint.getEncoding()),
                () -> assertEquals(ExchangePattern.InOut, thymeleafEndpoint.getExchangePattern()),
                () -> assertEquals(ORDER, thymeleafEndpoint.getOrder()),
                () -> assertEquals(PREFIX_WEB_INF_TEMPLATES, thymeleafEndpoint.getPrefix()),
                () -> assertEquals(ThymeleafResolverType.WEB_APP, thymeleafEndpoint.getResolver()),
                () -> assertEquals(HTML_SUFFIX, thymeleafEndpoint.getSuffix()),
                () -> assertNotNull(thymeleafEndpoint.getTemplateEngine()),
                () -> assertEquals(HTML, thymeleafEndpoint.getTemplateMode()));

        assertEquals(1, thymeleafEndpoint.getTemplateEngine().getTemplateResolvers().size());
        ITemplateResolver resolver = thymeleafEndpoint.getTemplateEngine().getTemplateResolvers().stream().findFirst().get();
        assertTrue(resolver instanceof WebApplicationTemplateResolver);

        WebApplicationTemplateResolver templateResolver = (WebApplicationTemplateResolver) resolver;
        assertAll("templateResolver",
                () -> assertTrue(templateResolver.isCacheable()),
                () -> assertEquals(CACHE_TIME_TO_LIVE, templateResolver.getCacheTTLMs()),
                () -> assertEquals(UTF_8_ENCODING, templateResolver.getCharacterEncoding()),
                () -> assertTrue(templateResolver.getCheckExistence()),
                () -> assertEquals(ORDER, templateResolver.getOrder()),
                () -> assertEquals(PREFIX_WEB_INF_TEMPLATES, templateResolver.getPrefix()),
                () -> assertEquals(HTML_SUFFIX, templateResolver.getSuffix()),
                () -> assertEquals(TemplateMode.HTML, templateResolver.getTemplateMode()));
    }

    private ServletContext servletContext() {

        if (servletContext == null) {
            servletContext = mock(ServletContext.class);
        }

        return servletContext;
    }

    private JakartaServletWebApplication jakartaServletWebApplication() {

        if (jakartaServletWebApplication == null) {
            jakartaServletWebApplication = JakartaServletWebApplication.buildApplication(servletContext());
        }

        return jakartaServletWebApplication;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {

            public void configure() throws Exception {

                ThymeleafEndpoint endpoint = new ThymeleafEndpoint();
                endpoint.setCamelContext(context);
                endpoint.setJakartaServletWebApplication(jakartaServletWebApplication());
                endpoint.setAllowContextMapAll(true);
                endpoint.setResourceUri("letter");
                endpoint.setPrefix("WEB-INF/templates/");
                endpoint.setSuffix(".html");
                endpoint.setResolver(ThymeleafResolverType.WEB_APP);
                endpoint.setCacheable(true);
                endpoint.setCacheTimeToLive(500L);
                endpoint.setEncoding("UTF-8");
                endpoint.setCheckExistence(true);
                endpoint.setOrder(1);
                endpoint.setTemplateMode("HTML");

                context.addEndpoint(TEST_ENDPOINT, endpoint);

                from(DIRECT_START)
                        .setBody(simple(SPAZZ_TESTING_SERVICE))
                        .to(TEST_ENDPOINT)
                        .to(MOCK_RESULT);
            }
        };
    }

}
