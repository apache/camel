package org.apache.camel.reifier;

import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.CamelContextHelper;

public abstract class AbstractReifier {

    protected static String parseString(RouteContext routeContext, String text) {
        return CamelContextHelper.parseText(routeContext.getCamelContext(), text);
    }

    protected static boolean parseBoolean(RouteContext routeContext, String text) {
        Boolean b = CamelContextHelper.parseBoolean(routeContext.getCamelContext(), text);
        return b != null && b;
    }

    protected static Long parseLong(RouteContext routeContext, String text) {
        return CamelContextHelper.parseLong(routeContext.getCamelContext(), text);
    }

    protected static Integer parseInt(RouteContext routeContext, String text) {
        return CamelContextHelper.parseInteger(routeContext.getCamelContext(), text);
    }

    protected static <T> T parse(RouteContext routeContext, Class<T> clazz, String text) {
        return CamelContextHelper.parse(routeContext.getCamelContext(), clazz, text);
    }

}
