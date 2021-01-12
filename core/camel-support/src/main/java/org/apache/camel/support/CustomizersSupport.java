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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatCustomizer;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageCustomizer;

public final class CustomizersSupport {
    private CustomizersSupport() {
    }

    /**
     * Determine the value of the "enabled" flag for a hierarchy of properties.
     *
     * @param  camelContext the {@link CamelContext}
     * @param  prefixes     an ordered list of prefixed (less restrictive to more restrictive)
     * @return              the value of the key `enabled` for most restrictive prefix
     */
    public static boolean isEnabled(CamelContext camelContext, String... prefixes) {
        boolean answer = true;

        // Loop over all the prefixes to find out the value of the key `enabled`
        // for the most restrictive prefix.
        for (String prefix : prefixes) {
            String property = prefix.endsWith(".") ? prefix + "enabled" : prefix + ".enabled";

            // evaluate the value of the current prefix using the parent one as
            // default value so if the `enabled` property is not set, the parent
            // one is used.
            answer = camelContext.getPropertiesComponent()
                    .resolveProperty(property)
                    .map(Boolean::valueOf)
                    .orElse(answer);
        }

        return answer;
    }

    /**
     * Base class for policies
     */
    private static class CamelContextAwarePolicy implements CamelContextAware {
        private CamelContext camelContext;

        @Override
        public CamelContext getCamelContext() {
            return this.camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }
    }

    /**
     * A {@link ComponentCustomizer.Policy} that uses a hierarchical lists of properties to determine if customization
     * is enabled for the given {@link org.apache.camel.Component}.
     */
    public static final class ComponentCustomizationEnabledPolicy
            extends CamelContextAwarePolicy
            implements ComponentCustomizer.Policy {

        @Override
        public boolean test(String name, Component target) {
            return isEnabled(
                    getCamelContext(),
                    "camel.customizer",
                    "camel.customizer.component",
                    "camel.customizer.component." + name);
        }
    }

    /**
     * A {@link DataFormatCustomizer.Policy} that uses a hierarchical lists of properties to determine if customization
     * is enabled for the given {@link org.apache.camel.spi.DataFormat}.
     */
    public static final class DataFormatCustomizationEnabledPolicy
            extends CamelContextAwarePolicy
            implements DataFormatCustomizer.Policy {

        @Override
        public boolean test(String name, DataFormat target) {
            return isEnabled(
                    getCamelContext(),
                    "camel.customizer",
                    "camel.customizer.dataformat",
                    "camel.customizer.dataformat." + name);
        }
    }

    /**
     * A {@link LanguageCustomizer.Policy} that uses a hierarchical lists of properties to determine if customization is
     * enabled for the given {@link org.apache.camel.spi.Language}.
     */
    public static final class LanguageCustomizationEnabledPolicy
            extends CamelContextAwarePolicy
            implements LanguageCustomizer.Policy {

        @Override
        public boolean test(String name, Language target) {
            return isEnabled(
                    getCamelContext(),
                    "camel.customizer",
                    "camel.customizer.language",
                    "camel.customizer.language." + name);
        }
    }
}
