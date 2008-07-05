/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.model.RouteType;
import org.apache.camel.processor.interceptor.Debugger;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.view.RouteDotGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * A command line tool for booting up a CamelContext using an optional Spring
 * ApplicationContext
 *
 * @version $Revision$
 */
public class Main extends ServiceSupport {
    private static final Log LOG = LogFactory.getLog(Main.class);
    private String applicationContextUri = "META-INF/spring/*.xml";
    private String fileApplicationContextUri;
    private AbstractApplicationContext applicationContext;
    private List<Option> options = new ArrayList<Option>();
    private CountDownLatch latch = new CountDownLatch(1);
    private AtomicBoolean completed = new AtomicBoolean(false);
    private long duration = -1;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private String dotOutputDir;
    private boolean aggregateDot;
    private boolean debug;
    private boolean trace;
    private List<RouteBuilder> routeBuilders = new ArrayList<RouteBuilder>();
    private List<SpringCamelContext> camelContexts = new ArrayList<SpringCamelContext>();
    private AbstractApplicationContext parentApplicationContext;
    private String parentApplicationContextUri;
    private ProducerTemplate camelTemplate;

    public Main() {
        addOption(new Option("h", "help", "Displays the help screen") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                showOptions();
                completed();
            }
        });

        addOption(new ParameterOption("a", "applicationContext",
                "Sets the classpath based spring ApplicationContext", "applicationContext") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setApplicationContextUri(parameter);
            }
        });

        addOption(new ParameterOption("fa", "fileApplicationContext",
                "Sets the filesystem based spring ApplicationContext", "fileApplicationContext") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setFileApplicationContextUri(parameter);
            }
        });

        addOption(new ParameterOption("o", "outdir",
                "Sets the DOT output directory where the visual representations of the routes are generated",
                "dot") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setDotOutputDir(parameter);
            }
        });
        addOption(new ParameterOption("ad", "aggregate-dot",
                "Aggregates all routes (in addition to individual route generation) into one context to create one monolithic DOT file for visual representations the entire system.",
                "aggregate-dot") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setAggregateDot("true".equals(parameter));
            }
        });
        addOption(new ParameterOption("d", "duration",
                "Sets the time duration that the applicaiton will run for, by default in milliseconds. You can use '10s' for 10 seconds etc",
                "duration") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                String value = parameter.toUpperCase();
                if (value.endsWith("S")) {
                    value = value.substring(0, value.length() - 1);
                    setTimeUnit(TimeUnit.SECONDS);
                }
                setDuration(Integer.parseInt(value));
            }
        });

        addOption(new Option("x", "debug", "Enables the debugger") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                enableDebug();
            }
        });
        addOption(new Option("t", "trace", "Enables tracing") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                enableTrace();
            }
        });
    }

    public static void main(String... args) {
        new Main().run(args);
    }

    /**
     * Parses the command line arguments then runs the program
     */
    public void run(String[] args) {
        parseArguments(args);
        run();
    }

    /**
     * Runs this process with the given arguments
     */
    public void run() {
        if (!completed.get()) {
            try {
                start();
                waitUntilCompleted();
                stop();
            } catch (Exception e) {
                LOG.error("Failed: " + e, e);
            }
        }
    }

    /**
     * Marks this process as being completed
     */
    public void completed() {
        completed.set(true);
        latch.countDown();
    }

    public void addRouteBuilder(RouteBuilder routeBuilder) {
        getRouteBuilders().add(routeBuilder);
    }

    /**
     * Displays the command line options
     */
    public void showOptions() {
        System.out.println("Apache Camel Runner takes the following options");
        System.out.println();

        for (Option option : options) {
            System.out.println("  " + option.getAbbreviation() + " or " + option.getFullName() + " = "
                    + option.getDescription());
        }
    }

    /**
     * Parses the commandl ine arguments
     */
    public void parseArguments(String[] arguments) {
        LinkedList<String> args = new LinkedList<String>(Arrays.asList(arguments));

        boolean valid = true;
        while (!args.isEmpty()) {
            String arg = args.removeFirst();

            boolean handled = false;
            for (Option option : options) {
                if (option.processOption(arg, args)) {
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                System.out.println("Unknown option: " + arg);
                System.out.println();
                valid = false;
                break;
            }
        }
        if (!valid) {
            showOptions();
            completed();
        }
    }

    public void addOption(Option option) {
        options.add(option);
    }

    public abstract class Option {
        private String abbreviation;
        private String fullName;
        private String description;

        protected Option(String abbreviation, String fullName, String description) {
            this.abbreviation = "-" + abbreviation;
            this.fullName = "-" + fullName;
            this.description = description;
        }

        public boolean processOption(String arg, LinkedList<String> remainingArgs) {
            if (arg.equalsIgnoreCase(abbreviation) || fullName.startsWith(arg)) {
                doProcess(arg, remainingArgs);
                return true;
            }
            return false;
        }

        public String getAbbreviation() {
            return abbreviation;
        }

        public String getDescription() {
            return description;
        }

        public String getFullName() {
            return fullName;
        }

        protected abstract void doProcess(String arg, LinkedList<String> remainingArgs);
    }

    public abstract class ParameterOption extends Option {
        private String parameterName;

        protected ParameterOption(String abbreviation, String fullName, String description,
                String parameterName) {
            super(abbreviation, fullName, description);
            this.parameterName = parameterName;
        }

        protected void doProcess(String arg, LinkedList<String> remainingArgs) {
            if (remainingArgs.isEmpty()) {
                System.err.println("Expected fileName for ");
                showOptions();
                completed();
            } else {
                String parameter = remainingArgs.removeFirst();
                doProcess(arg, parameter, remainingArgs);
            }
        }

        protected abstract void doProcess(String arg, String parameter, LinkedList<String> remainingArgs);
    }

    // Properties
    // -------------------------------------------------------------------------
    public AbstractApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(AbstractApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public String getApplicationContextUri() {
        return applicationContextUri;
    }

    public void setApplicationContextUri(String applicationContextUri) {
        this.applicationContextUri = applicationContextUri;
    }

    public String getFileApplicationContextUri() {
        return fileApplicationContextUri;
    }

    public void setFileApplicationContextUri(String fileApplicationContextUri) {
        this.fileApplicationContextUri = fileApplicationContextUri;
    }

    public AbstractApplicationContext getParentApplicationContext() {
        if (parentApplicationContext == null) {
            if (parentApplicationContextUri != null) {
                parentApplicationContext = new ClassPathXmlApplicationContext(parentApplicationContextUri);
                parentApplicationContext.start();
            }
        }
        return parentApplicationContext;
    }

    public void setParentApplicationContext(AbstractApplicationContext parentApplicationContext) {
        this.parentApplicationContext = parentApplicationContext;
    }

    public String getParentApplicationContextUri() {
        return parentApplicationContextUri;
    }

    public void setParentApplicationContextUri(String parentApplicationContextUri) {
        this.parentApplicationContextUri = parentApplicationContextUri;
    }

    public List<SpringCamelContext> getCamelContexts() {
        return camelContexts;
    }

    public long getDuration() {
        return duration;
    }

    /**
     * Sets the duration to run the application for in milliseconds until it
     * should be terminated. Defaults to -1. Any value <= 0 will run forever.
     *
     * @param duration
     */
    public void setDuration(long duration) {
        this.duration = duration;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Sets the time unit duration
     */
    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public String getDotOutputDir() {
        return dotOutputDir;
    }

    /**
     * Sets the output directory of the generated DOT Files to show the visual
     * representation of the routes. A null value disables the dot file
     * generation
     */
    public void setDotOutputDir(String dotOutputDir) {
        this.dotOutputDir = dotOutputDir;
    }

    public List<RouteBuilder> getRouteBuilders() {
        return routeBuilders;
    }

    public void setRouteBuilders(List<RouteBuilder> routeBuilders) {
        this.routeBuilders = routeBuilders;
    }

    public void setAggregateDot(boolean aggregateDot) {
        this.aggregateDot = aggregateDot;
    }

    public boolean isAggregateDot() {
        return aggregateDot;
    }

    public boolean isDebug() {
        return debug;
    }

    public void enableDebug() {
        this.debug = true;
        setParentApplicationContextUri("/META-INF/services/org/apache/camel/spring/debug.xml");
    }

    public boolean isTrace() {
        return trace;
    }

    public void enableTrace() {
        this.trace = true;
        setParentApplicationContextUri("/META-INF/services/org/apache/camel/spring/trace.xml");
    }

    /**
     * Returns the currently active debugger if one is enabled
     *
     * @return the current debugger or null if none is active
     * @see #enableDebug()
     */
    public Debugger getDebugger() {
        for (SpringCamelContext camelContext : camelContexts) {
            Debugger debugger = Debugger.getDebugger(camelContext);
            if (debugger != null) {
                return debugger;
            }
        }
        return null;
    }

    public List<RouteType> getRouteDefinitions() {
        List<RouteType> answer = new ArrayList<RouteType>();
        for (SpringCamelContext camelContext : camelContexts) {
            answer.addAll(camelContext.getRouteDefinitions());
        }
        return answer;
    }

    /**
     * Returns a {@link ProducerTemplate} from the Spring {@link ApplicationContext} instances
     * or lazily creates a new one dynamically
     *
     * @return
     */
    public ProducerTemplate getCamelTemplate() {
        if (camelTemplate == null) {
            camelTemplate = findOrCreateCamelTemplate();
        }
        return camelTemplate;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected ProducerTemplate findOrCreateCamelTemplate() {
        String[] names = getApplicationContext().getBeanNamesForType(ProducerTemplate.class);
        if (names != null && names.length > 0) {
            return (ProducerTemplate) getApplicationContext().getBean(names[0], ProducerTemplate.class);
        }
        for (SpringCamelContext camelContext : camelContexts) {
            return camelContext.createProducerTemplate();
        }
        throw new IllegalArgumentException("No CamelContexts are available so cannot create a ProducerTemplate!");
    }

    protected void doStart() throws Exception {
        LOG.info("Apache Camel " + getVersion() + " starting");
        if (applicationContext == null) {
            applicationContext = createDefaultApplicationContext();
        }
        applicationContext.start();

        postProcessContext();
    }

    protected AbstractApplicationContext createDefaultApplicationContext() {
        // file based
        if (getFileApplicationContextUri() != null) {
            String[] args = getFileApplicationContextUri().split(";");

            ApplicationContext parentContext = getParentApplicationContext();
            if (parentContext != null) {
                return new FileSystemXmlApplicationContext(args, parentContext);
            } else {
                return new FileSystemXmlApplicationContext(args);
            }
        }
        
        // default to classpath based
        String[] args = getApplicationContextUri().split(";");
        ApplicationContext parentContext = getParentApplicationContext();
        if (parentContext != null) {
            return new ClassPathXmlApplicationContext(args, parentContext);
        } else {
            return new ClassPathXmlApplicationContext(args);
        }
    }

    protected void doStop() throws Exception {
        LOG.info("Apache Camel terminating");

        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    protected void waitUntilCompleted() {
        while (!completed.get()) {
            try {
                if (duration > 0) {
                    TimeUnit unit = getTimeUnit();
                    LOG.info("Waiting for: " + duration + " " + unit);
                    latch.await(duration, unit);
                    completed.set(true);
                } else {
                    latch.await();
                }
            } catch (InterruptedException e) {
                LOG.debug("Caught: " + e);
            }
        }
    }

    protected void postProcessContext() throws Exception {
        Map<String, SpringCamelContext> map = applicationContext.getBeansOfType(SpringCamelContext.class);
        Set<Map.Entry<String, SpringCamelContext>> entries = map.entrySet();
        int size = entries.size();
        for (Map.Entry<String, SpringCamelContext> entry : entries) {
            String name = entry.getKey();
            SpringCamelContext camelContext = entry.getValue();
            camelContexts.add(camelContext);
            generateDot(name, camelContext, size);
            postProcesCamelContext(camelContext);
        }

        if (isAggregateDot()) {
            generateDot("aggregate", aggregateSpringCamelContext(applicationContext), 1);
        }
    }

    protected void generateDot(String name, SpringCamelContext camelContext, int size) throws IOException {
        String outputDir = dotOutputDir;
        if (ObjectHelper.isNotNullAndNonEmpty(outputDir)) {
            if (size > 1) {
                outputDir += "/" + name;
            }
            RouteDotGenerator generator = new RouteDotGenerator(outputDir);
            LOG.info("Generating DOT file for routes: " + outputDir + " for: " + camelContext + " with name: " + name);
            generator.drawRoutes(camelContext);
        }
    }

    /**
     * Used for aggregate dot generation
     *
     * @param applicationContext
     * @return
     * @throws Exception
     */
    private static SpringCamelContext aggregateSpringCamelContext(ApplicationContext applicationContext) throws Exception {
        SpringCamelContext aggregateCamelContext = new SpringCamelContext() {
            /**
             *  Don't actually start this, it is merely fabricated for dot generation.
             * @see org.apache.camel.impl.DefaultCamelContext#shouldStartRoutes()
             */
            protected boolean shouldStartRoutes() {

                return false;
            }
        };

        // look up all configured camel contexts
        String[] names = applicationContext.getBeanNamesForType(SpringCamelContext.class);
        for (String name : names) {

            SpringCamelContext next = (SpringCamelContext) applicationContext.getBean(name, SpringCamelContext.class);
            //            aggregateCamelContext.addRoutes( next.getRoutes() );
            aggregateCamelContext.addRouteDefinitions(next.getRouteDefinitions());
        }
        // Don't actually start this, it is merely fabricated for dot generation.
        //        answer.setApplicationContext( applicationContext );
        //        answer.afterPropertiesSet();
        return aggregateCamelContext;
    }

    protected void postProcesCamelContext(CamelContext camelContext) throws Exception {
        for (RouteBuilder routeBuilder : routeBuilders) {
            camelContext.addRoutes(routeBuilder);
        }
    }

    protected String getVersion() {
        Package aPackage = Package.getPackage("org.apache.camel");
        if (aPackage != null) {
            String version = aPackage.getImplementationVersion();
            if (version == null) {
                version = aPackage.getSpecificationVersion();
                if (version == null) {
                    version = "";
                }
            }
            return version;
        }
        return "";
    }
}
