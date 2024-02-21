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

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThymeleafFileResolverAllParamsTest extends ThymeleafAbstractBaseTest {

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

        ThymeleafEndpoint thymeleafEndpoint = context.getEndpoint(
                "thymeleaf:org/apache/camel/component/thymeleaf/letter?allowContextMapAll=true&cacheTimeToLive=500&cacheable=false&checkExistence=true&encoding=UTF-8&order=1&prefix=src/test/resources/&templateMode=HTML&resolver=FILE&suffix=.html",
                ThymeleafEndpoint.class);

        assertAll("properties",
                () -> assertNotNull(thymeleafEndpoint),
                () -> assertTrue(thymeleafEndpoint.isAllowContextMapAll()),
                () -> assertFalse(thymeleafEndpoint.getCacheable()),
                () -> assertEquals(CACHE_TIME_TO_LIVE, thymeleafEndpoint.getCacheTimeToLive()),
                () -> assertTrue(thymeleafEndpoint.getCheckExistence()),
                () -> assertEquals(UTF_8_ENCODING, thymeleafEndpoint.getEncoding()),
                () -> assertEquals(ExchangePattern.InOut, thymeleafEndpoint.getExchangePattern()),
                () -> assertEquals(ORDER, thymeleafEndpoint.getOrder()),
                () -> assertEquals(PREFIX_SRC_TEST_RESOURCES, thymeleafEndpoint.getPrefix()),
                () -> assertEquals(ThymeleafResolverType.FILE, thymeleafEndpoint.getResolver()),
                () -> assertEquals(HTML_SUFFIX, thymeleafEndpoint.getSuffix()),
                () -> assertNotNull(thymeleafEndpoint.getTemplateEngine()),
                () -> assertEquals(HTML, thymeleafEndpoint.getTemplateMode()));

        assertEquals(1, thymeleafEndpoint.getTemplateEngine().getTemplateResolvers().size());
        ITemplateResolver resolver = thymeleafEndpoint.getTemplateEngine().getTemplateResolvers().stream().findFirst().get();
        assertTrue(resolver instanceof FileTemplateResolver);

        FileTemplateResolver templateResolver = (FileTemplateResolver) resolver;
        assertAll("templateResolver",
                () -> assertFalse(templateResolver.isCacheable()),
                () -> assertEquals(CACHE_TIME_TO_LIVE, templateResolver.getCacheTTLMs()),
                () -> assertEquals(UTF_8_ENCODING, templateResolver.getCharacterEncoding()),
                () -> assertTrue(templateResolver.getCheckExistence()),
                () -> assertEquals(ORDER, templateResolver.getOrder()),
                () -> assertEquals(PREFIX_SRC_TEST_RESOURCES, templateResolver.getPrefix()),
                () -> assertEquals(HTML_SUFFIX, templateResolver.getSuffix()),
                () -> assertEquals(TemplateMode.HTML, templateResolver.getTemplateMode()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {

            public void configure() {

                from(DIRECT_START)
                        .setBody(simple(SPAZZ_TESTING_SERVICE))
                        .to("thymeleaf:org/apache/camel/component/thymeleaf/letter?allowContextMapAll=true&cacheTimeToLive=500&cacheable=false&checkExistence=true&encoding=UTF-8&order=1&prefix=src/test/resources/&templateMode=HTML&resolver=FILE&suffix=.html")
                        .to(MOCK_RESULT);
            }
        };
    }

}
