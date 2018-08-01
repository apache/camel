package org.slf4j;

import org.slf4j.impl.StaticLoggerBinder;

public final class LoggerFactory {

    private LoggerFactory() {
    }

    public static Logger getLogger(String name) {
        ILoggerFactory iLoggerFactory = getILoggerFactory();
        return iLoggerFactory.getLogger(name);
    }

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    public static ILoggerFactory getILoggerFactory() {
        return StaticLoggerBinder.getSingleton().getLoggerFactory();
    }
}
