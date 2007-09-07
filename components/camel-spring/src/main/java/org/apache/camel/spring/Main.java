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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.view.RouteDotGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A command line tool for booting up a CamelContext using an optional Spring
 * ApplicationContext
 *
 * @version $Revision: $
 */
public class Main extends ServiceSupport {
    private static final Log LOG = LogFactory.getLog(Main.class);
    private String applicationContextUri = "META-INF/spring/*.xml";
    private AbstractApplicationContext applicationContext;
    private List<Option> options = new ArrayList<Option>();
    private CountDownLatch latch = new CountDownLatch(1);
    private AtomicBoolean completed = new AtomicBoolean(false);
    private long duration = -1;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private String dotOutputDir;

    public Main() {
        addOption(new Option("h", "help", "Displays the help screen") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                showOptions();
                completed();
            }
        });

        addOption(new ParameterOption("a", "applicationContext", "Sets the classpath based pring ApplicationContext", "applicationContext") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setApplicationContextUri(parameter);
            }
        });
        addOption(new ParameterOption("o", "outdir", "Sets the DOT output directory where the visual representations of the routes are generated", "dot") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setDotOutputDir(parameter);
            }
        });
        addOption(new ParameterOption("d", "duration", "Sets the time duration that the applicaiton will run for, by default in milliseconds. You can use '10s' for 10 seconds etc", "duration") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                String value = parameter.toUpperCase();
                if (value.endsWith("S")) {
                    value = value.substring(0, value.length() - 1);
                    setTimeUnit(TimeUnit.SECONDS);
                }
                setDuration(Integer.parseInt(value));
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
                postProcessContext();
                waitUntilCompleted();
                stop();
            }
            catch (Exception e) {
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

    /**
     * Displays the command line options
     */
    public void showOptions() {
        System.out.println("Apache Camel Runner takes the following options");
        System.out.println();

        for (Option option : options) {
            System.out.println("  " + option.getAbbreviation() + " or " + option.getFullName() + " = " + option.getDescription());
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

        protected ParameterOption(String abbreviation, String fullName, String description, String parameterName) {
            super(abbreviation, fullName, description);
            this.parameterName = parameterName;
        }

        protected void doProcess(String arg, LinkedList<String> remainingArgs) {
            if (remainingArgs.isEmpty()) {
                System.err.println("Expected fileName for ");
                showOptions();
                completed();
            }
            else {
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

    public long getDuration() {
        return duration;
    }

    /**
     * Sets the duration to run the application for in milliseconds until it should be terminated.
     * Defaults to -1. Any value <= 0 will run forever.
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
     * Sets the output directory of the generated DOT Files
     * to show the visual representation of the routes.
     * A null value disables the dot file generation
     */
    public void setDotOutputDir(String dotOutputDir) {
        this.dotOutputDir = dotOutputDir;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected void doStart() throws Exception {
        LOG.info("Apache Camel " + getVersion() + " starting");
        if (applicationContext == null) {
            applicationContext = createDefaultApplicationContext();
        }
        applicationContext.start();
    }

    protected AbstractApplicationContext createDefaultApplicationContext() {
        String[] args = getApplicationContextUri().split(";");
        return new ClassPathXmlApplicationContext(args);
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
                }
                else {
                    latch.await();
                }
            }
            catch (InterruptedException e) {
                LOG.debug("Caught: " + e);
            }
        }
    }

    protected void postProcessContext() throws Exception {
        if (ObjectHelper.isNotNullAndNonEmpty(dotOutputDir)) {
            RouteDotGenerator generator = new RouteDotGenerator(dotOutputDir);
            CamelContext camelContext = SpringCamelContext.springCamelContext(applicationContext);
            LOG.info("Generating DOT file for routes: " + dotOutputDir + " for: " + camelContext);
            generator.drawRoutes(camelContext);
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
