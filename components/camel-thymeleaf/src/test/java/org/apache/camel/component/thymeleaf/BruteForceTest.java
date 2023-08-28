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

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.DefaultTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BruteForceTest {

    private static final String TEMPLATE_NAME = "letter";

    private static final Logger LOG = LoggerFactory.getLogger(BruteForceTest.class);

    @Test
    public void testClassLoaderTemplateResolverTxt() {

        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/org/apache/camel/component/thymeleaf/");
        resolver.setSuffix(".txt");
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testClassLoaderTemplateResolverHtml() {

        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/org/apache/camel/component/thymeleaf/");
        resolver.setSuffix(".html");
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testClassLoaderTemplateResolverXml() {

        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/org/apache/camel/component/thymeleaf/");
        resolver.setSuffix(".xml");
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testClassLoaderTemplateResolverHtmlAsXml() {

        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/org/apache/camel/component/thymeleaf/");
        resolver.setSuffix(".xml");
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testDefaultTemplateResolverTxt() {

        TemplateEngine templateEngine = new TemplateEngine();
        DefaultTemplateResolver resolver = new DefaultTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setTemplate(textTemplate());
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testDefaultTemplateResolverHtml() {

        TemplateEngine templateEngine = new TemplateEngine();
        DefaultTemplateResolver resolver = new DefaultTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setTemplate(textTemplate());
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testFileTemplateResolverTxt() {

        TemplateEngine templateEngine = new TemplateEngine();
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("src/test/resources/org/apache/camel/component/thymeleaf/");
        resolver.setSuffix(".txt");
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testFileTemplateResolverHtml() {

        TemplateEngine templateEngine = new TemplateEngine();
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("src/test/resources/org/apache/camel/component/thymeleaf/");
        resolver.setSuffix(".html");
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testFileTemplateResolverTxtAsHtml() {

        TemplateEngine templateEngine = new TemplateEngine();
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("src/test/resources/org/apache/camel/component/thymeleaf/");
        resolver.setSuffix(".txt");
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testFileTemplateResolverHtmlAsTxt() {

        TemplateEngine templateEngine = new TemplateEngine();
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("src/test/resources/org/apache/camel/component/thymeleaf/");
        resolver.setSuffix(".html");
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testFileTemplateResolverNoMode() {

        TemplateEngine templateEngine = new TemplateEngine();
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("src/test/resources/org/apache/camel/component/thymeleaf/");
        resolver.setSuffix(".html");
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testStringTemplateResolverTxt() {

        TemplateEngine templateEngine = new TemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(textTemplate(), context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testStringTemplateResolverHtml() {

        TemplateEngine templateEngine = new TemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(textTemplate(), context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    protected Context context() {

        Context context = new Context();
        context.setVariable("headers", headers());
        context.setVariable("body", body());

        return context;
    }

    protected Map<String, Object> headers() {

        Map<String, Object> headers = new HashMap<>();
        headers.put("lastName", "Doe");
        headers.put("firstName", "Jane");
        headers.put("item", "Widgets for Dummies");

        return headers;
    }

    protected Object body() {

        return "Spazz Testing Service";
    }

    protected String textTemplate() {

        return """
                <!--/*-->
                    Licensed to the Apache Software Foundation (ASF) under one or more
                    contributor license agreements.  See the NOTICE file distributed with
                    this work for additional information regarding copyright ownership.
                    The ASF licenses this file to You under the Apache License, Version 2.0
                    (the "License"); you may not use this file except in compliance with
                    the License.  You may obtain a copy of the License at

                         http://www.apache.org/licenses/LICENSE-2.0

                    Unless required by applicable law or agreed to in writing, software
                    distributed under the License is distributed on an "AS IS" BASIS,
                    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                    See the License for the specific language governing permissions and
                    limitations under the License.
                <!--*/-->
                <!--/* This code will be removed at thymeleaf parsing time! */-->
                Dear [(${headers.lastName})], [(${headers.firstName})]

                Thanks for the order of [(${headers.item})].

                Regards Camel Riders Bookstore
                [(${body})]""";
    }

}
