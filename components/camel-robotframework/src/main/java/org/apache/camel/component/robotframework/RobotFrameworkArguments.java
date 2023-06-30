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
package org.apache.camel.component.robotframework;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.util.ObjectHelper;

public class RobotFrameworkArguments {

    private final List<String> arguments = new ArrayList<>();

    public void addFileToArguments(String name, String flag) {
        if (name != null) {
            File file = new File(name);
            if (isFileValid(file)) {
                String path = !file.getName().equalsIgnoreCase("NONE") ? file.getPath() : file.getName();
                add(flag, path);
            }
        }
    }

    public void addFileToArguments(File file, String flag) {
        if (isFileValid(file)) {
            String path = !file.getName().equalsIgnoreCase("NONE") ? file.getPath() : file.getName();
            add(flag, path);
        }
    }

    protected boolean isFileValid(File file) {
        return file != null && file.getPath() != null && !file.getPath().isEmpty();
    }

    public void addNonEmptyStringToArguments(String variableToAdd, String flag) {
        if (!ObjectHelper.isEmpty(variableToAdd)) {
            addStringToArguments(variableToAdd, flag);
        }
    }

    public void addFlagToArguments(boolean flag, String argument) {
        if (flag) {
            add(argument);
        }
    }

    public void addStringToArguments(String variableToAdd, String flag) {
        add(flag, variableToAdd);
    }

    public void addListToArguments(String variablesToAdd, String flag) {
        if (variablesToAdd == null) {
            return;
        }
        String[] splittedVariablesToAdd = variablesToAdd.split(",");
        addListToArguments(new ArrayList<>(Arrays.asList(splittedVariablesToAdd)), flag);
    }

    public void addListToArguments(List<String> variablesToAdd, String flag) {
        if (variablesToAdd == null) {
            return;
        }
        for (String variableToAdd : variablesToAdd) {
            if (!ObjectHelper.isEmpty(variableToAdd)) {
                add(flag, variableToAdd);
            }
        }
    }

    public void addFileListToArguments(List<File> variablesToAdd, String flag) {
        if (variablesToAdd == null) {
            return;
        }
        for (File variableToAdd : variablesToAdd) {
            addFileToArguments(variableToAdd, flag);
        }
    }

    public void add(String... values) {
        arguments.addAll(Arrays.asList(values));
    }

    public String[] toArray() {
        return arguments.toArray(new String[0]);
    }

}
