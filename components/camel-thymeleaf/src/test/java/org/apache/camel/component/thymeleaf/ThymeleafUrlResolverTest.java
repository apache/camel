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

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.UrlTemplateResolver;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WireMockTest(httpPort = 9843)
public class ThymeleafUrlResolverTest extends ThymeleafAbstractBaseTest {

    @Test
    public void testThymeleaf() throws InterruptedException {

        stubFor(get("/dontcare.html").willReturn(ok(fragment())));

        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(1);
        mock.message(0).body().contains(YOU_WILL_NOTIFIED);
        mock.message(0).header(ThymeleafConstants.THYMELEAF_TEMPLATE).isNull();
        mock.message(0).header(FIRST_NAME).isEqualTo(JANE);

        template.request(DIRECT_START, urlTemplateHeaderProcessor);

        mock.assertIsSatisfied();

        ThymeleafEndpoint thymeleafEndpoint = context.getEndpoint(
                "thymeleaf:dontcare?allowContextMapAll=true&resolver=URL",
                ThymeleafEndpoint.class);

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
                () -> assertEquals(ThymeleafResolverType.URL, thymeleafEndpoint.getResolver()),
                () -> assertNull(thymeleafEndpoint.getSuffix()),
                () -> assertNotNull(thymeleafEndpoint.getTemplateEngine()),
                () -> assertNull(thymeleafEndpoint.getTemplateMode()));

        assertEquals(1, thymeleafEndpoint.getTemplateEngine().getTemplateResolvers().size());
        ITemplateResolver resolver = thymeleafEndpoint.getTemplateEngine().getTemplateResolvers().stream().findFirst().get();
        assertTrue(resolver instanceof UrlTemplateResolver);

        UrlTemplateResolver templateResolver = (UrlTemplateResolver) resolver;
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

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {

            public void configure() {

                from(DIRECT_START)
                        .to("thymeleaf:dontcare?allowContextMapAll=true&resolver=URL")
                        .to(MOCK_RESULT);
            }
        };
    }

    protected String fragment() {

        return """
                <span th:fragment="test" th:remove="tag">
                You will be notified when your order ships.
                </span>
                """;
    }

}
