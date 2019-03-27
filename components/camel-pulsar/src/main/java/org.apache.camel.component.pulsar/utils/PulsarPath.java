package org.apache.camel.component.pulsar.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PulsarPath {
    private static final Pattern pattern = Pattern.compile("^((persistent|non-persistent)://)?(?<namespace>(?<tenant>.+)/.+)/.+$");

    private String tenant;
    private String namespace;
    private boolean autoConfigurable;

    public PulsarPath(String path) {
        Matcher matcher = pattern.matcher(path);
        autoConfigurable = matcher.matches();
        if (autoConfigurable) {
            tenant = matcher.group("tenant");
            namespace = matcher.group("namespace");
        }
    }
    public String getTenant() {
        return tenant;
    }

    public String getNamespace() {
        return namespace;
    }

    public boolean isAutoConfigurable() {
        return autoConfigurable;
    }
}