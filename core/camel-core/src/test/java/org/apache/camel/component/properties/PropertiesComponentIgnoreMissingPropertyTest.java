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
package org.apache.camel.component.properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.support.EndpointHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PropertiesComponentIgnoreMissingPropertyTest extends ContextTestSupport {

    @Test
    public void testIgnoreMissingProperty() throws Exception {
        var o = context.getPropertiesComponent().resolveProperty("{{?foo}}");
        Assertions.assertFalse(o.isEmpty());
        Assertions.assertEquals("{{?foo}}", o.get());

        o = context.getPropertiesComponent().resolveProperty("{{foo}}");
        Assertions.assertFalse(o.isEmpty());
        Assertions.assertEquals("{{foo}}", o.get());

        o = context.getPropertiesComponent().resolveProperty("{{myQueueSize}}");
        Assertions.assertFalse(o.isEmpty());
        Assertions.assertEquals("10", o.get());

        String out = EndpointHelper.resolveEndpointUriPropertyPlaceholders(context, "{{?foo}}");
        Assertions.assertEquals("{{?foo}}", out);

        out = EndpointHelper.resolveEndpointUriPropertyPlaceholders(context, "{{foo}}");
        Assertions.assertEquals("{{foo}}", out);

        out = EndpointHelper.resolveEndpointUriPropertyPlaceholders(context, "foo:dummy?a=1&b={{foo}}&c={{myQueueSize}}");
        Assertions.assertEquals("foo:dummy?a=1&b={{foo}}&c=10", out);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        context.getPropertiesComponent().setIgnoreMissingProperty(true);
        return context;
    }

}
