package org.apache.camel.builder.component;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.GeneratedPropertyConfigurer;

public abstract class AbstractComponentBuilder<C extends Component> {
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public C build() {
        final C component = buildConcreteComponent();
        if (component.getCamelContext() == null) {
            component.setCamelContext(new DefaultCamelContext());
        }
        component.init();

        final GeneratedPropertyConfigurer propertyConfigurer = (GeneratedPropertyConfigurer) component.getComponentPropertyConfigurer();

        configureComponentProperties(component, propertyConfigurer);

        return component;
    }

    public C build(final CamelContext context) {
        final C component = buildConcreteComponent();
        component.setCamelContext(context);
        component.init();

        final GeneratedPropertyConfigurer propertyConfigurer = (GeneratedPropertyConfigurer) component.getComponentPropertyConfigurer();

        configureComponentProperties(component, propertyConfigurer);

        return component;
    }

    public void doSetProperty(final String key, final Object value) {
        properties.put(key, value);
    }

    protected abstract C buildConcreteComponent();

    private void configureComponentProperties(final Component component, final GeneratedPropertyConfigurer propertyConfigurer) {
        final CamelContext context = component.getCamelContext();
        properties.forEach((key, value) -> propertyConfigurer.configure(context, component, key, resolvePropertyValue(value, context), false));
    }

    private Object resolvePropertyValue(final Object value, final CamelContext context) {
        if (value instanceof String) {
            return context.resolvePropertyPlaceholders((String) value);
        }
        return value;
    }
}

