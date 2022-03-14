package org.apache.camel.main;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.Configuration;
import org.apache.camel.Converter;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.dsl.support.CompilePostProcessor;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.util.ObjectHelper;

public class CamelAnnotationSupport {

    public static void registerCamelSupport(CamelContext context) {
        context.getRegistry().bind("CamelTypeConverterCompilePostProcessor", new TypeConverterCompilePostProcessor());
        context.getRegistry().bind("CamelBindToRegistryCompilePostProcessor", new BindToRegistryCompilePostProcessor());
    }

    private static class TypeConverterCompilePostProcessor implements CompilePostProcessor {

        @Override
        public void postCompile(CamelContext camelContext, String name, Class<?> clazz, Object instance) throws Exception {
            if (clazz.getAnnotation(Converter.class) != null) {
                TypeConverterRegistry tcr = camelContext.getTypeConverterRegistry();
                TypeConverterExists exists = tcr.getTypeConverterExists();
                LoggingLevel level = tcr.getTypeConverterExistsLoggingLevel();
                // force type converter to override as we could be re-loading
                tcr.setTypeConverterExists(TypeConverterExists.Override);
                tcr.setTypeConverterExistsLoggingLevel(LoggingLevel.OFF);
                try {
                    tcr.addTypeConverters(clazz);
                } finally {
                    tcr.setTypeConverterExists(exists);
                    tcr.setTypeConverterExistsLoggingLevel(level);
                }
            }
        }

    }

    private static class BindToRegistryCompilePostProcessor implements CompilePostProcessor {

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

}
