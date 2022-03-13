package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.support.BindToRegistryCompilePostProcessor;
import org.apache.camel.dsl.support.TypeConverterCompilePostProcessor;

public class CamelAnnotationSupport {

    public static void registerCamelSupport(CamelContext context) {
        context.getRegistry().bind("CamelTypeConverterCompilePostProcessor", new TypeConverterCompilePostProcessor());
        context.getRegistry().bind("CamelBindToRegistryCompilePostProcessor", new BindToRegistryCompilePostProcessor());
    }

}
