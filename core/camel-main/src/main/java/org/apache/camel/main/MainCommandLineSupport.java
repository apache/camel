/*
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
package org.apache.camel.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Support for command line arguments to Camel main.
 */
public abstract class MainCommandLineSupport extends MainSupport {

    protected final List<Option> options = new ArrayList<>();

    public MainCommandLineSupport(Class... configurationClasses) {
        super(configurationClasses);
    }

    public MainCommandLineSupport() {
        addOption(new Option("h", "help", "Displays the help screen") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                showOptions();
                completed();
            }
        });
        addOption(new ParameterOption("r", "routers",
                "Sets the router builder classes which will be loaded while starting the camel context",
                "routerBuilderClasses") {
            @Override
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setRouteBuilderClasses(parameter);
            }
        });
        addOption(new ParameterOption("d", "duration",
                "Sets the time duration (seconds) that the application will run for before terminating.",
                "duration") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                // skip second marker to be backwards compatible
                if (parameter.endsWith("s") || parameter.endsWith("S")) {
                    parameter = parameter.substring(0, parameter.length() - 1);
                }
                configure().setDurationMaxSeconds(Integer.parseInt(parameter));
            }
        });
        addOption(new ParameterOption("dm", "durationMaxMessages",
                "Sets the duration of maximum number of messages that the application will process before terminating.",
                "durationMaxMessages") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                configure().setDurationMaxMessages(Integer.parseInt(parameter));
            }
        });
        addOption(new ParameterOption("di", "durationIdle",
                "Sets the idle time duration (seconds) duration that the application can be idle before terminating.",
                "durationIdle") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                // skip second marker to be backwards compatible
                if (parameter.endsWith("s") || parameter.endsWith("S")) {
                    parameter = parameter.substring(0, parameter.length() - 1);
                }
                configure().setDurationMaxIdleSeconds(Integer.parseInt(parameter));
            }
        });
        addOption(new Option("t", "trace", "Enables tracing") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                enableTrace();
            }
        });
        addOption(new ParameterOption("e", "exitcode",
                "Sets the exit code if duration was hit",
                "exitcode") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                configure().setDurationHitExitCode(Integer.parseInt(parameter));
            }
        });
        addOption(new ParameterOption("pl", "propertiesLocation",
                "Sets location(s) to load properties, such as from classpath or file system.",
                "propertiesLocation") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setPropertyPlaceholderLocations(parameter);
            }
        });
    }

    /**
     * Displays the command line options.
     */
    public void showOptions() {
        showOptionsHeader();

        for (Option option : options) {
            System.out.println(option.getInformation());
        }
    }

    /**
     * Parses the command line arguments.
     */
    public void parseArguments(String[] arguments) {
        LinkedList<String> args = new LinkedList<>(Arrays.asList(arguments));

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

    /**
     * Parses the command line arguments then runs the program.
     */
    public void run(String[] args) throws Exception {
        parseArguments(args);
        run();
        LOG.info("MainSupport exiting code: {}", getExitCode());
    }

    /**
     * Displays the header message for the command line options.
     */
    public void showOptionsHeader() {
        System.out.println("Apache Camel Runner takes the following options");
        System.out.println();
    }


    public abstract class ParameterOption extends Option {
        private String parameterName;

        protected ParameterOption(String abbreviation, String fullName, String description, String parameterName) {
            super(abbreviation, fullName, description);
            this.parameterName = parameterName;
        }

        @Override
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

        @Override
        public String getInformation() {
            return "  " + getAbbreviation() + " or " + getFullName() + " <" + parameterName + "> = " + getDescription();
        }

        protected abstract void doProcess(String arg, String parameter, LinkedList<String> remainingArgs);
    }
}
