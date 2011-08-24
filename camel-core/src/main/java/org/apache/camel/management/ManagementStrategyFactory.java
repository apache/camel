package org.apache.camel.management;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManagementStrategyFactory {
    private final transient Logger log = LoggerFactory.getLogger(getClass());

    public ManagementStrategy create(CamelContext context, boolean disableJMX) {
        ManagementStrategy answer = null;

        if (disableJMX || Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
            log.info("JMX is disabled. Using DefaultManagementStrategy.");
        } else {
            try {
                log.info("JMX enabled. Using ManagedManagementStrategy.");
                answer = new ManagedManagementStrategy(new DefaultManagementAgent(context));
                // must start it to ensure JMX works and can load needed Spring JARs
                ServiceHelper.startService(answer);
                // prefer to have it at first strategy
                context.getLifecycleStrategies().add(0, new DefaultManagementLifecycleStrategy(context));
            } catch (Exception e) {
                answer = null;
                log.warn("Cannot create JMX lifecycle strategy. Fallback to using DefaultManagementStrategy (non JMX).", e);
            }
        }

        if (answer == null) {
            answer = new DefaultManagementStrategy();
        }
        return answer;
    }
}
