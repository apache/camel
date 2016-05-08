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
package org.apache.camel.dataformat.bindy.format.factories;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.dataformat.bindy.Format;
import org.apache.camel.dataformat.bindy.FormattingOptions;

/**
 * This class manages all FormatFactoryInterfaces.
 * This class is a singleton class.
 * FormatFactoryInterfaces can declare to support one or more classes or
 * can declare to be generic (e.g. {@link EnumFormatFactory}).
 * The factories that support one or more classes are stored in a Map.
 * The generic factories are stored in a list.
 * The build method first tries to find a factory using the map.
 * If it doesn't find one it uses the generic list.
 * If it can't find a factory it throws an IllegalArgumentException.
 */
public final class FormatFactories extends AbstractFormatFactory {

    private static final FormatFactories INSTANCE = new FormatFactories();
    private static final Map<Class<?>, List<FormatFactoryInterface>> CLASS_BASED_FACTORIES = new HashMap<>();
    private static final List<FormatFactoryInterface> OTHER_FACTORIES = new ArrayList<>();

    private FormatFactories() {
    }

    public static FormatFactories getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a {@link FormatFactoryInterface}.
     * Two types of factories exist:
     * <ul>
     * <li>Factories that support one or more classes</li>
     * <li>Factories that support no specific class (e.g. {@link EnumFormatFactory})</li>
     * </ul>
     * @param formatFactory
     * @return the FormatFactories instance
     */
    public synchronized FormatFactories register(FormatFactoryInterface formatFactory) {
        if (formatFactory.supportedClasses().isEmpty()) {
            OTHER_FACTORIES.add(formatFactory);
        } else {
            for (Class<?> clazz : formatFactory.supportedClasses()) {
                getByClass(clazz).add(formatFactory);
            }
        }
        return this;
    }

    @Override
    public boolean canBuild(FormattingOptions formattingOptions) {
        return true;
    }

    @Override
    public Format<?> build(FormattingOptions formattingOptions) {
        for (FormatFactoryInterface formatFactory : getByClass(formattingOptions.getClazz())) {
            if (formatFactory.canBuild(formattingOptions)) {
                return formatFactory.build(formattingOptions);
            }
        }
        for (FormatFactoryInterface formatFactory : OTHER_FACTORIES) {
            if (formatFactory.canBuild(formattingOptions)) {
                return formatFactory.build(formattingOptions);
            }
        }
        throw new IllegalArgumentException("Can not find a suitable formatter for the type: " + formattingOptions.getClazz().getCanonicalName());
    }

    private List<FormatFactoryInterface> getByClass(Class<?> clazz) {
        List<FormatFactoryInterface> result = CLASS_BASED_FACTORIES.get(clazz);
        if (result == null) {
            result = new ArrayList<>();
            CLASS_BASED_FACTORIES.put(clazz, result);
        }
        return result;
    }
}
