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
package org.apache.camel.spring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A command line tool for booting up a CamelContext using an
 * optional Spring ApplicationContext
 *
 * @version $Revision: $
 */
public class Main {

    private String applicationContextUri;

    private List<Option> options = new ArrayList<Option>();
    private CountDownLatch latch = new CountDownLatch(1);
    private AtomicBoolean completed = new AtomicBoolean(false);

    public static void main(String[] args) {
        Main main = new Main();
        main.run(args);
    }

    public Main() {
        addOption(new Option("h", "help", "Displays the help screen") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                showOptions();
                completed();
            }
        });
    }


    /**
     * Runs this process with the given arguments
     */
    public void run(String[] args) {
        if (!parseArguments(args)) {
            showOptions();
            return;
        }
        if (!completed.get()) {
            start();
            waitUntilCompleted();
            stop();
        }
    }

    protected void start() {
        // TODO
    }

    protected void stop() {
        // TODO
    }

    protected void waitUntilCompleted() {
        while (!completed.get()) {
            try {
                latch.await();
            }
            catch (InterruptedException e) {
                // ignore
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

        for (Option option : options) {
            System.out.println("  -" + option.getAbbreviation() + " or -" + option.getFullName()
                    + " = " + option.getDescription());
        }
    }

    public boolean parseArguments(String[] arguments) {
        LinkedList<String> args = new LinkedList<String>(Arrays.asList(arguments));

        if (args.isEmpty()) {
            return false;
        }
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
                return false;
            }
        }
        return true;

    }


    public void addOption(Option option) {
        options.add(option);
    }


    public abstract class Option {
        private String abbreviation;
        private String fullName;
        private String description;

        protected Option(String abbreviation, String fullName, String description) {
            this.abbreviation = abbreviation;
            this.fullName = fullName;
            this.description = description;
        }

        public boolean processOption(String arg, LinkedList<String> remainingArgs) {
            if (arg.equalsIgnoreCase(abbreviation) || fullName.startsWith(fullName)) {
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

}
