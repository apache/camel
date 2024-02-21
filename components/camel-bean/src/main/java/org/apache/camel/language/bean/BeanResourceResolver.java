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
package org.apache.camel.language.bean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.ResourceResolver;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.ResourceResolverSupport;
import org.apache.camel.support.ResourceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ResourceResolver(BeanResourceResolver.SCHEME)
public class BeanResourceResolver extends ResourceResolverSupport {
    public static final String SCHEME = "bean";
    private static final Logger LOG = LoggerFactory.getLogger(BeanResourceResolver.class);

    public BeanResourceResolver() {
        super(SCHEME);
    }

    @Override
    public Resource createResource(String location, String remaining) {
        LOG.trace("Creating resource from calling bean: {}", remaining);

        return new ResourceSupport(SCHEME, location) {
            @Override
            public boolean exists() {
                return false;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                final CamelContext context = getCamelContext();
                final Exchange dummy = new DefaultExchange(context);

                InputStream answer = null;
                Object out = evaluate(dummy, remaining);

                if (dummy.getException() != null) {
                    throw new IOException(
                            "Cannot find resource: " + location + " from calling the bean", dummy.getException());
                }

                if (out != null) {
                    answer = context.getTypeConverter().tryConvertTo(InputStream.class, dummy, out);
                    if (answer == null) {
                        String text = context.getTypeConverter().tryConvertTo(String.class, dummy, out);
                        if (text != null) {
                            answer = new ByteArrayInputStream(text.getBytes());
                        }
                    }
                }

                if (answer == null) {
                    throw new IOException("Cannot find resource: " + location + " from calling the bean");
                }

                return answer;
            }
        };
    }

    private Object evaluate(Exchange dummy, String expression) {
        return getCamelContext()
                .resolveLanguage(BeanLanguage.LANGUAGE)
                .createExpression(expression)
                .evaluate(dummy, Object.class);
    }
}
