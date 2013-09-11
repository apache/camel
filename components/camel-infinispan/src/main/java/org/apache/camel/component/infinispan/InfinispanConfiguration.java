package org.apache.camel.component.infinispan;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.infinispan.commons.api.BasicCacheContainer;

public class InfinispanConfiguration {
    private BasicCacheContainer cacheContainer;
    private String caseName;
    private String host;
    private String command;
    private boolean sync = true;
    private Set<String> eventTypes;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public BasicCacheContainer getCacheContainer() {
        return cacheContainer;
    }

    public void setCacheContainer(BasicCacheContainer cacheContainer) {
        this.cacheContainer = cacheContainer;
    }

    public String getCasheName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public Set<String> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(Set<String> eventTypes) {
        this.eventTypes = eventTypes;
    }

    public void setEventTypes(String eventTypes) {
        this.eventTypes = new HashSet<String>(Arrays.asList(eventTypes.split(",")));
    }
}
