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
package org.apache.camel.component.jasypt;

import java.util.LinkedList;
import java.util.Map;
import javax.xml.bind.JAXBException;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.MainSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.view.ModelFileGenerator;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

/**
 * @version $Revision$
 */
public class Main extends MainSupport {

    private StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();

    private String command;
    private String password;
    private String input;
    private String algorithm;

    public Main() {
        options.clear();

        addOption(new Option("h", "help", "Displays the help screen") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                showOptions();
                completed();
            }
        });

        addOption(new ParameterOption("c", "command", "Command either encrypt or decrypt", "command") {
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
    }

    @Override
    public void showOptionsHeader() {
        System.out.println("Apache Camel Jasypt takes the following options");
        System.out.println();
    }

    protected ProducerTemplate findOrCreateCamelTemplate() {
        // noop
        return null;
    }

    protected Map<String, CamelContext> getCamelContextMap() {
        // noop
        return null;
    }

    protected ModelFileGenerator createModelFileGenerator() throws JAXBException {
        // noop
        return null;
    }

    @Override
    public void run() throws Exception {
        if (ObjectHelper.isEmpty(command)) {
            throw new IllegalArgumentException("Command is empty");
        }
        if (ObjectHelper.isEmpty(password)) {
            throw new IllegalArgumentException("Password is empty");
        }
        if (ObjectHelper.isEmpty(input)) {
            throw new IllegalArgumentException("Input is empty");
        }

        encryptor.setPassword(password);
        if (algorithm != null) {
            encryptor.setAlgorithm(algorithm);
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
