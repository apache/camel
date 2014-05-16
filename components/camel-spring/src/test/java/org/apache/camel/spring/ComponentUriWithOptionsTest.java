/**
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
package org.apache.camel.spring;

import org.apache.camel.component.seda.SedaEndpoint;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ComponentUriWithOptionsTest extends SpringTestSupport {
    public void testUriOptionsOnFromTag() {
        SedaEndpoint input = resolveMandatoryEndpoint("seda:input", SedaEndpoint.class);

        assertEquals(1, input.getSize());
    }

    public void testUriOptionsOnToTag() {
        SedaEndpoint output = resolveMandatoryEndpoint("seda:output", SedaEndpoint.class);

        assertEquals(2, output.getSize());
    }

    public void testUriOptionsOnInOutTag() {
        SedaEndpoint inOut = resolveMandatoryEndpoint("seda:inOut", SedaEndpoint.class);

        assertEquals(3, inOut.getSize());
    }

    public void testUriOptionsOnInOnlyTag() {
        SedaEndpoint inOnly = resolveMandatoryEndpoint("seda:inOnly", SedaEndpoint.class);

        assertEquals(4, inOnly.getSize());
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/componentUriWithOptions.xml");
    }
}
