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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

public class ThymeleafWebApplicationResolverTest extends ThymeleafAbstractBaseTest {

    private static final String TEST_ENDPOINT = "testEndpoint";

    private ServletContext servletContext;

    private JakartaServletWebApplication jakartaServletWebApplication;

    @BeforeEach
    public void setUp() throws Exception {

        super.setUp();

        File initialFile = new File("src/test/resources/WEB-INF/templates/letter.html");
        InputStream targetStream = FileUtils.openInputStream(initialFile);
        Mockito.when(servletContext.getResourceAsStream(anyString())).thenReturn(targetStream);
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
                () -> assertNull(thymeleafEndpoint.getCacheable()),
                () -> assertNull(thymeleafEndpoint.getCacheTimeToLive()),
                () -> assertNull(thymeleafEndpoint.getCheckExistence()),
                () -> assertNull(thymeleafEndpoint.getEncoding()),
                () -> assertEquals(ExchangePattern.InOut, thymeleafEndpoint.getExchangePattern()),
                () -> assertNull(thymeleafEndpoint.getOrder()),
                () -> assertNull(thymeleafEndpoint.getPrefix()),
                () -> assertEquals(ThymeleafResolverType.WEB_APP, thymeleafEndpoint.getResolver()),
                () -> assertNull(thymeleafEndpoint.getSuffix()),
                () -> assertNotNull(thymeleafEndpoint.getTemplateEngine()),
                () -> assertNull(thymeleafEndpoint.getTemplateMode()));

        assertEquals(1, thymeleafEndpoint.getTemplateEngine().getTemplateResolvers().size());
        ITemplateResolver resolver = thymeleafEndpoint.getTemplateEngine().getTemplateResolvers().stream().findFirst().get();
        assertTrue(resolver instanceof WebApplicationTemplateResolver);

        WebApplicationTemplateResolver templateResolver = (WebApplicationTemplateResolver) resolver;
        assertAll("templateResolver",
                () -> assertTrue(templateResolver.isCacheable()),
                () -> assertNull(templateResolver.getCacheTTLMs()),
                () -> assertNull(templateResolver.getCharacterEncoding()),
                () -> assertFalse(templateResolver.getCheckExistence()),
                () -> assertNull(templateResolver.getOrder()),
                () -> assertNull(templateResolver.getPrefix()),
                () -> assertNull(templateResolver.getSuffix()),
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
                endpoint.setResourceUri("WEB-INF/templates/letter.html");
                endpoint.setResolver(ThymeleafResolverType.WEB_APP);

                context.addEndpoint(TEST_ENDPOINT, endpoint);

                from(DIRECT_START)
                        .setBody(simple(SPAZZ_TESTING_SERVICE))
                        .to(TEST_ENDPOINT)
                        .to(MOCK_RESULT);
            }
        };
    }

}
