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
import org.thymeleaf.templateresolver.StringTemplateResolver;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BruteForceJavascriptTest {

    private static final String TEMPLATE_NAME = "letter";

    private static final Logger LOG = LoggerFactory.getLogger(BruteForceJavascriptTest.class);

    @Test
    public void testClassLoaderTemplateResolverJavascript() {

        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.JAVASCRIPT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/org/apache/camel/component/thymeleaf/");
        resolver.setSuffix(".js");
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testClassLoaderTemplateResolverTxt() {

        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("/org/apache/camel/component/thymeleaf/");
        resolver.setSuffix(".js");
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
        resolver.setSuffix(".js");
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testDefaultTemplateResolverJavascript() {

        TemplateEngine templateEngine = new TemplateEngine();
        DefaultTemplateResolver resolver = new DefaultTemplateResolver();
        resolver.setTemplateMode(TemplateMode.JAVASCRIPT);
        resolver.setTemplate(jsTemplate());
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
        resolver.setTemplate(jsTemplate());
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
        resolver.setTemplate(jsTemplate());
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(TEMPLATE_NAME, context());
        LOG.info("\n" + result.trim());

        assertNotNull(result);
        assertTrue((result.length() > 0));
    }

    @Test
    public void testStringTemplateResolverJavascript() {

        TemplateEngine templateEngine = new TemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.JAVASCRIPT);
        templateEngine.setTemplateResolver(resolver);

        String result = templateEngine.process(jsTemplate(), context());
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

        String result = templateEngine.process(jsTemplate(), context());
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

        String result = templateEngine.process(jsTemplate(), context());
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
        headers.put("showContentButton", "testShowContentButton");
        headers.put("hideContentButton", "testHideContentButton");

        return headers;
    }

    protected Object body() {

        return "testSomeContent";
    }

    protected String jsTemplate() {

        return """
                <!--/*
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

                    Comments are not supported in JAVASCRIPT templates, so this ASF header will appear in the rendered text.
                */-->
                <span th:fragment="show-hide-content" th:remove="tag">
                    <script th:inline="javascript">
                        function hideContent() {
                            showContentButton = document.getElementById(/*[[${headers.showContentButton}]]*/ 'showContentButton');
                            hideContentButton = document.getElementById(/*[[${headers.hideContentButton}]]*/ 'hideContentButton');
                            someContent = document.getElementById(/*[[${body}]]*/ 'someContent');

                            showContentButton.style.display = 'block';
                            hideContentButton.style.display = 'none';
                            someContent.style.display = 'none';
                        }

                        function showContent() {
                            showContentButton = document.getElementById(/*[[${headers.showContentButton}]]*/ 'showContentButton');
                            hideContentButton = document.getElementById(/*[[${headers.hideContentButton}]]*/ 'hideContentButton');
                            someContent = document.getElementById(/*[[${body}]]*/ 'someContent');

                            showContentButton.style.display = 'none';
                            hideContentButton.style.display = 'block';
                            someContent.style.display = 'block';
                        }
                    </script>
                </span>
                """;
    }

}
