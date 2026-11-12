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
package org.apache.camel.component.controlbus;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The language command of the control bus is off until its language is in the allowedLanguages option.
 */
public class ControlBusLanguageAllowedTest extends ContextTestSupport {

    @Test
    public void testLanguageOffByDefault() {
        Exception e = assertThrows(Exception.class,
                () -> context.getEndpoint("controlbus:language:simple"));
        assertAllowedLanguagesError(e, "simple");
    }

    @Test
    public void testLanguageNotInList() {
        Exception e = assertThrows(Exception.class,
                () -> context.getEndpoint("controlbus:language:simple?allowedLanguages=groovy"));
        assertAllowedLanguagesError(e, "simple");
    }

    @Test
    public void testLanguageInEndpointList() {
        String name = template.requestBody("controlbus:language:simple?allowedLanguages=groovy, simple",
                "${camelContext.name}", String.class);
        assertEquals(context.getName(), name);
    }

    @Test
    public void testLanguageInComponentList() {
        context.getComponent("controlbus", ControlBusComponent.class).setAllowedLanguages("simple");

        String name = template.requestBody("controlbus:language:simple", "${camelContext.name}", String.class);
        assertEquals(context.getName(), name);
    }

    @Test
    public void testEndpointListOverridesComponentList() {
        context.getComponent("controlbus", ControlBusComponent.class).setAllowedLanguages("simple");

        Exception e = assertThrows(Exception.class,
                () -> context.getEndpoint("controlbus:language:simple?allowedLanguages=constant"));
        assertAllowedLanguagesError(e, "simple");
    }

    private static void assertAllowedLanguagesError(Exception e, String language) {
        ResolveEndpointFailedException re = assertInstanceOf(ResolveEndpointFailedException.class, e);
        Throwable cause = re.getCause();
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("allowedLanguages"), cause.getMessage());
        assertTrue(cause.getMessage().contains(" " + language + " "), cause.getMessage());
    }
}
