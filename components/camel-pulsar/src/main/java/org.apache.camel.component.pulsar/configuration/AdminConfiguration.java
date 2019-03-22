package org.apache.camel.component.pulsar.configuration;

import org.apache.pulsar.client.impl.conf.ClientConfigurationData;

import java.util.Set;

public class AdminConfiguration extends ClientConfigurationData {

    private boolean autoCreateAllowed;
    private Set<String> clusters;

    public boolean isAutoCreateAllowed() {
        return autoCreateAllowed && clusters != null && !clusters.isEmpty();
    }

    public void setAutoCreateAllowed(boolean autoCreateAllowed) {
        this.autoCreateAllowed = autoCreateAllowed;
    }

    public Set<String> getClusters() {
        return clusters;
    }

    public void setClusters(Set<String> clusters) {
        this.clusters = clusters;
    }
}
