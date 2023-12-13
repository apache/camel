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
package org.apache.camel.component.jasypt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;

public class Main {

    private final StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
    private final List<Option> options = new ArrayList<>();
    private String command;
    private String password;
    private String input;
    private String algorithm;
    private String randomSaltGeneratorAlgorithm;
    private String randomIvGeneratorAlgorithm;

    private abstract class Option {
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

        public String getInformation() {
            return "  " + getAbbreviation() + " or " + getFullName() + " = " + getDescription();
        }

        protected abstract void doProcess(String arg, LinkedList<String> remainingArgs);
    }

    private abstract class ParameterOption extends Option {
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
            } else {
                String parameter = remainingArgs.removeFirst();
                doProcess(arg, parameter, remainingArgs);
            }
        }

        @Override
        public String getInformation() {
            return "  " + getAbbreviation() + " or " + getFullName()
                   + " <" + parameterName + "> = " + getDescription();
        }

        protected abstract void doProcess(String arg, String parameter, LinkedList<String> remainingArgs);
    }

    public Main() {
        addOption(new Option("h", "help", "Displays the help screen") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                showOptions();
                // no need to process further if user just wants help
                System.exit(0);
            }
        });

        addOption(new ParameterOption("c", "command", "Command can be encrypt or decrypt", "command") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                if ("encrypt".equals(parameter) || "decrypt".equals(parameter)) {
                    command = parameter;
                } else {
                    throw new IllegalArgumentException("Unknown command, was: " + parameter);
                }
            }
        });

        addOption(new ParameterOption("p", "password", "Password to use", "password") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                password = parameter;
            }
        });

        addOption(new ParameterOption("i", "input", "Text to encrypt or decrypt", "input") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                input = parameter;
            }
        });

        addOption(new ParameterOption("a", "algorithm", "Optional algorithm to use", "algorithm") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                algorithm = parameter;
            }
        });

        addOption(new ParameterOption("rsga", "salt", "Optional random salt generator algorithm to use", "salt") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                randomSaltGeneratorAlgorithm = parameter;
            }
        });

        addOption(new ParameterOption("riga", "iv", "Optional random iv generator algorithm to use", "iv") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                randomIvGeneratorAlgorithm = parameter;
            }
        });
    }

    private void addOption(Option option) {
        options.add(option);
    }

    private void showOptions() {
        System.out.println("Apache Camel Jasypt takes the following options:");
        System.out.println();
        for (Option option : options) {
            System.out.println(option.getInformation());
        }
        System.out.println();
        System.out.println();
    }

    private boolean parseArguments(String[] arguments) {
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
                System.out.println("Error: Unknown option: " + arg);
                System.out.println();
                valid = false;
                break;
            }
        }

        return valid;
    }

    public void run(String[] args) {
        if (!parseArguments(args)) {
            showOptions();
            return;
        }

        if (command == null) {
            System.out.println("Error: Command is empty");
            System.out.println();
            showOptions();
            return;
        }
        if (password == null) {
            System.out.println("Error: Password is empty");
            System.out.println();
            showOptions();
            return;
        }
        if (input == null) {
            System.out.println("Error: Input is empty");
            System.out.println();
            showOptions();
            return;
        }

        encryptor.setPassword(password);
        if (algorithm != null) {
            encryptor.setAlgorithm(algorithm);
        }
        if (randomSaltGeneratorAlgorithm != null) {
            encryptor.setSaltGenerator(new RandomSaltGenerator(randomSaltGeneratorAlgorithm));
        }
        if (randomIvGeneratorAlgorithm != null) {
            encryptor.setIvGenerator(new RandomIvGenerator(randomIvGeneratorAlgorithm));
        }
        if ("encrypt".equals(command)) {
            System.out.println("Encrypted text: " + encryptor.encrypt(input));
        } else {
            System.out.println("Decrypted text: " + encryptor.decrypt(input));
        }
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        if (args.length == 0) {
            main.showOptions();
            return;
        } else {
            main.run(args);
        }
    }

}
