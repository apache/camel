package org.apache.camel.spi;

import java.util.Map;

public interface ManagementNamingStrategyAware {
    Map<String, String> getManagedAttributes();
}
