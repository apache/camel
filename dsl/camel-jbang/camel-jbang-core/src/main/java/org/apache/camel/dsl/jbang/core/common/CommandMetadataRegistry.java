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
package org.apache.camel.dsl.jbang.core.common;

import java.util.Set;

import org.aesh.command.metadata.CommandMetadataProvider;
import org.aesh.command.metadata.MetadataRegistry;

/**
 * The aesh metadata of the commands in the given packages, which the aesh annotation processor generates at compile
 * time: aesh then does not read the annotations of a command at run time, which creates a proxy class for each kind of
 * annotation.
 * <p>
 * The metadata of a command is the class {@code <command class>_AeshMetadata} in the package of the command (a nested
 * class {@code Outer$Inner} has {@code Outer_Inner_AeshMetadata}). The registry that the processor generates is not
 * used, as it is in the package of the command that the processor happens to see first.
 * <p>
 * Only the commands of the given packages are looked up: all the commands of the CLI and its plugins are in packages of
 * {@code org.apache.camel.dsl.jbang.core.commands}, and a class that is not there costs a search of the whole class
 * path.
 * <p>
 * aesh looks up the metadata of a command class, and then of its super classes, so a command without metadata of its
 * own gets the metadata of the command it extends, which misses its own options. A command that a plugin extends with
 * options of its own (Export, by KubernetesExport) is therefore excluded: aesh reads the annotations of both at run
 * time. CommandTree fails when a command would get the metadata of its super class while it has options of its own.
 *
 * @param classLoader the class loader of the commands
 * @param packages    the packages of the commands
 * @param excluded    the classes to read the annotations of at run time (and their super classes without metadata)
 */
public record CommandMetadataRegistry(ClassLoader classLoader, Set<String> packages, Set<String> excluded)
        implements
            MetadataRegistry {

    private static final String SUFFIX = "_AeshMetadata";

    @Override
    public CommandMetadataProvider<?> get(String commandClassName) {
        int dot = commandClassName.lastIndexOf('.');
        if (dot < 0 || !packages.contains(commandClassName.substring(0, dot)) || excluded.contains(commandClassName)) {
            return null;
        }
        String simpleName = commandClassName.substring(dot + 1);
        int nested = simpleName.indexOf('$');
        if (nested >= 0 && nested + 1 < simpleName.length() && Character.isDigit(simpleName.charAt(nested + 1))) {
            // an anonymous or local class has no metadata of its own (aesh uses the one of its super class)
            return null;
        }
        String name = commandClassName.substring(0, dot + 1) + simpleName.replace('$', '_') + SUFFIX;
        try {
            Class<?> type = Class.forName(name, true, classLoader);
            return (CommandMetadataProvider<?>) type.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create the aesh metadata " + name, e);
        }
    }
}
