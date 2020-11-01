package org.apache.camel.main;

import org.apache.camel.spi.BootstrapCloseable;

public class MainBootstrapCloseable implements BootstrapCloseable {

    private final MainSupport main;

    public MainBootstrapCloseable(MainSupport main) {
        this.main = main;
    }

    @Override
    public void close() {
        // we are now bootstrapped and can clear up memory
        if (main.initialProperties != null) {
            main.initialProperties.clear();
            main.initialProperties = null;
        }
        if (main.overrideProperties != null) {
            main.overrideProperties.clear();
            main.overrideProperties = null;
        }
        main.wildcardProperties.clear();
        main.wildcardProperties = null;

        // no longer in use
        main.mainConfigurationProperties.close();
        main.mainConfigurationProperties = null;
        main.routesCollector = null;
    }
}
