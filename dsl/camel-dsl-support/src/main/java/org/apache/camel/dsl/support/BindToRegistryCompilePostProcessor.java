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
package org.apache.camel.dsl.support;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.Configuration;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.util.ObjectHelper;

public class BindToRegistryCompilePostProcessor implements CompilePostProcessor {

    // TODO: move to camel-kamelet-main

    @Override
    public void postCompile(CamelContext camelContext, String name, Class<?> clazz, Object instance) throws Exception {
        BindToRegistry bir = instance.getClass().getAnnotation(BindToRegistry.class);
        Configuration cfg = instance.getClass().getAnnotation(Configuration.class);
        if (bir != null || cfg != null || instance instanceof CamelConfiguration) {
            CamelBeanPostProcessor bpp = camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor();
            if (bir != null && ObjectHelper.isNotEmpty(bir.value())) {
                name = bir.value();
            } else if (cfg != null && ObjectHelper.isNotEmpty(cfg.value())) {
                name = cfg.value();
            }
            // to support hot reloading of beans then we need to enable unbind mode in bean post processor
            bpp.setUnbindEnabled(true);
            try {
                // this class is a bean service which needs to be post processed and registered which happens
                // automatic by the bean post processor
                bpp.postProcessBeforeInitialization(instance, name);
                bpp.postProcessAfterInitialization(instance, name);
            } finally {
                bpp.setUnbindEnabled(false);
            }
            if (instance instanceof CamelConfiguration) {
                ((CamelConfiguration) instance).configure(camelContext);
            }
        }
    }

}
