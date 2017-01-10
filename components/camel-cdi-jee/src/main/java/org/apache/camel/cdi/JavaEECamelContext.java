package org.apache.camel.cdi;

import java.util.logging.Logger;

/**
 * A Camel context specialized for JavaEE environments
 */
@Vetoed
public class JavaEECamelContext extends CdiCamelContext {

    private static Logger logger = Logger.getLogger(JavaEECamelContext.class.getCanonicalName());

    private boolean startAllowed;

    public JavaEECamelContext() {
        super();
        startAllowed = false;
    }

    /**
     * Use this property to suppress starting the context initiated by
     * Camel-CDI. In some circumstances it might be useful to start the context
     * once the entire deployment is up and running.
     * <p>
     * Example for a CDI based context starter:
     * 
     * <pre>
     * &#64;javax.ejb.Singleton
     * &#64;javax.ejb.Startup
     * public class CamelBooter {
     *     &#64;javax.inject.Inject
     *     private JavaEECamelContext camelContext;
     * 
     *     &#64;javax.annotation.PostConstruct
     *     public void initialize() throws Exception {
     *         camelContext.setStartAllowed(true);
     *         camelContext.start();
     *     }
     * }
     * </pre>
     * 
     * @param startAllowed
     *            Whether the context should be started on any attempts
     */
    public void setStartAllowed(boolean startAllowed) {
        this.startAllowed = startAllowed;
    }

    @Override
    public void start() throws Exception {
        if (!startAllowed) {
            logger.info("Interupting attempt to start context '" + getName()
                    + "' because context is not allowed to start yet.");
            return;
        }
        super.start();
    }

}
