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
import org.apache.camel.dataformat.bindy.FormattingOptions;

/**
 * This class manages all FormatFactoryInterfaces.
 * FormatFactoryInterfaces can declare to support one or more classes or
 * can declare to be generic (e.g. {@link EnumFormatFactory}).
 * The factories that support one or more classes are stored in a Map.
 * The generic factories are stored in a list.
 * The build method first tries to findForFormattingOptions a factory using the map.
 * If it doesn't findForFormattingOptions one it uses the generic list.
 * If it can't findForFormattingOptions a factory it throws an IllegalArgumentException.
 */
public final class DefaultFactoryRegistry implements FactoryRegistry {

    private final Map<Class<?>, List<FormatFactoryInterface>> classBasedFactories = new HashMap<>();
    private final List<FormatFactoryInterface> otherFactories = new ArrayList<>();

    public DefaultFactoryRegistry() {
        this.register(new StringFormatFactory())
                .register(new DateFormatFactory())
                .register(new BooleanFormatFactory())
                .register(new BigIntegerFormatFactory())
                .register(new LocalTimeFormatFactory())
                .register(new LocalDateTimeFormatFactory())
                .register(new LocalDateFormatFactory())
                .register(new CharacterFormatFactory())
                .register(new EnumFormatFactory())
                .register(new BigDecimalFormatFactory())
                .register(new BigDecimalPatternFormatFactory())
                .register(new DoubleFormatFactory())
                .register(new DoublePatternFormatFactory())
                .register(new FloatFormatFactory())
                .register(new FloatPatternFormatFactory())
                .register(new LongFormatFactory())
                .register(new LongPatternFormatFactory())
                .register(new IntegerFormatFactory())
                .register(new IntegerPatternFormatFactory())
                .register(new ShortFormatFactory())
                .register(new ShortPatternFormatFactory())
                .register(new ByteFormatFactory())
                .register(new BytePatternFormatFactory());
    }

    /**
     * Registers a {@link FormatFactoryInterface}.
     * Two types of factories exist:
     * <ul>
     * <li>Factories that support one or more classes</li>
     * <li>Factories that support no specific class (e.g. {@link EnumFormatFactory})</li>
     * </ul>
     * @param formatFactories
     * @return the DefaultFactoryRegistry instance
     */
    @Override
    public FactoryRegistry register(FormatFactoryInterface... formatFactories) {
        for (FormatFactoryInterface formatFactory : formatFactories) {
            if (formatFactory.supportedClasses().isEmpty()) {
                for (FormatFactoryInterface factory : otherFactories) {
                    if (factory.getClass() == formatFactory.getClass()) {
                        return this;
                    }
                }
                otherFactories.add(formatFactory);
            } else {
                for (Class<?> clazz : formatFactory.supportedClasses()) {
                    List<FormatFactoryInterface> factories = getByClass(clazz);
                    for (FormatFactoryInterface factory : factories) {
                        if (factory.getClass() == formatFactory.getClass()) {
                            return this;
                        }
                    }
                    factories.add(formatFactory);
                }
            }
        }
        return this;
    }

    @Override
    public FactoryRegistry unregister(Class<? extends FormatFactoryInterface> clazz) {
        for (Map.Entry<Class<?>, List<FormatFactoryInterface>> entry : classBasedFactories.entrySet()) {
            entry.getValue().stream().filter(factory -> factory.getClass() == clazz).forEach(factory -> {
                entry.getValue().remove(factory);
            });
        }
        return this;
    }

    @Override
    public FormatFactoryInterface findForFormattingOptions(FormattingOptions formattingOptions) {
        for (FormatFactoryInterface formatFactory : getByClass(formattingOptions.getClazz())) {
            if (formatFactory.canBuild(formattingOptions)) {
                return formatFactory;
            }
        }
        for (FormatFactoryInterface formatFactory : otherFactories) {
            if (formatFactory.canBuild(formattingOptions)) {
                return formatFactory;
            }
        }
        throw new IllegalArgumentException("Can not findForFormattingOptions a suitable formatter for the type: " + formattingOptions.getClazz().getCanonicalName());
    }

    private List<FormatFactoryInterface> getByClass(Class<?> clazz) {
        List<FormatFactoryInterface> result = classBasedFactories.get(clazz);
        if (result == null) {
            result = new ArrayList<>();
            classBasedFactories.put(clazz, result);
        }
        return result;
    }
}
