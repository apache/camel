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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.dsl.support.CompilePostProcessor;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.util.ObjectHelper;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

public final class SpringAnnotationSupport {

    private SpringAnnotationSupport() {
    }

    public static void registerSpringSupport(CamelContext context) {
        context.getRegistry().bind("SpringAnnotationCompilePostProcessor", new SpringAnnotationCompilePostProcessor());
    }

    private static class SpringAnnotationCompilePostProcessor implements CompilePostProcessor {

        @Override
        public void postCompile(CamelContext camelContext, String name, Class<?> clazz, Object instance) throws Exception {
            // @Component and @Service are the same
            Component comp = clazz.getAnnotation(Component.class);
            Service service = clazz.getAnnotation(Service.class);
            if (comp != null || service != null) {
                CamelBeanPostProcessor bpp = camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor();
                if (comp != null && ObjectHelper.isNotEmpty(comp.value())) {
                    name = comp.value();
                } else if (service != null && ObjectHelper.isNotEmpty(service.value())) {
                    name = service.value();
                }
                // to support hot reloading of beans then we need to enable unbind mode in bean post processor
                bpp.setUnbindEnabled(true);
                try {
                    // re-bind the bean to the registry
                    camelContext.getRegistry().unbind(name);
                    camelContext.getRegistry().bind(name, instance);
                    // this class is a bean service which needs to be post processed
                    bpp.postProcessBeforeInitialization(instance, name);
                    bpp.postProcessAfterInitialization(instance, name);
                } finally {
                    bpp.setUnbindEnabled(false);
                }
            }
        }
    }
}
