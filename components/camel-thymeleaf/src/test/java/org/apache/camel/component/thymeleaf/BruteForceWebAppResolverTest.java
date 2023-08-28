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
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletContext;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

public class BruteForceWebAppResolverTest {

    private static final String TEMPLATE_NAME = "letter";

    private static final Logger LOG = LoggerFactory.getLogger(BruteForceWebAppResolverTest.class);

    private JakartaServletWebApplication servletWebApplication;

    @BeforeEach
    public void setUp() throws Exception {

        ServletContext servletContext = mock(ServletContext.class);

        servletWebApplication = JakartaServletWebApplication.buildApplication(servletContext);

        File initialFile = new File("src/test/resources/WEB-INF/templates/letter.html");
        InputStream targetStream = FileUtils.openInputStream(initialFile);
        Mockito.when(servletContext.getResourceAsStream(anyString())).thenReturn(targetStream);
    }

    @Test
    public void testWebApplicationTemplateResolverHtml() {

        TemplateEngine templateEngine = new TemplateEngine();

        WebApplicationTemplateResolver resolver = new WebApplicationTemplateResolver(servletWebApplication);
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/WEB-INF/templates/");
        resolver.setSuffix(".html");

        templateEngine.addTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.debug("\n" + result);

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testWebApplicationTemplateResolverTxt() {

        TemplateEngine templateEngine = new TemplateEngine();

        WebApplicationTemplateResolver resolver = new WebApplicationTemplateResolver(servletWebApplication);
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/WEB-INF/templates/");
        resolver.setSuffix(".html");

        templateEngine.addTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.debug("\n" + result);

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

}
