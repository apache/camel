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

package org.apache.camel.language;

import static org.assertj.core.api.Assertions.fail;

import javax.xml.xpath.XPathExpressionException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Expression;
import org.apache.camel.catalog.impl.CamelContextJSonSchemaResolver;
import org.apache.camel.catalog.impl.DefaultRuntimeCamelCatalog;
import org.apache.camel.spi.Language;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class XPathLanguageValidateTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testValidate() throws Exception {
        Language lan = context.resolveLanguage("xpath");

        Expression exp = lan.createExpression("/path/to");
        exp.init(context);

        try {
            exp = lan.createExpression("\\/path/to");
            exp.init(context);
            fail();
        } catch (Exception e) {
            assertIsInstanceOf(XPathExpressionException.class, e.getCause());
        }
    }

    @Test
    public void testValidateCatalog() throws Exception {
        DefaultRuntimeCamelCatalog c = new DefaultRuntimeCamelCatalog(false);
        c.setJSonSchemaResolver(new CamelContextJSonSchemaResolver(context));

        var r = c.validateLanguageExpression(null, "xpath", "/path/to");
        Assertions.assertTrue(r.isSuccess());
        Assertions.assertNull(r.getError());

        r = c.validateLanguageExpression(null, "xpath", "\\/path/to");
        Assertions.assertFalse(r.isSuccess());
        Assertions.assertTrue(r.getError().startsWith("javax.xml.xpath.XPathExpressionException"));
    }
}
