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
package org.apache.camel.spi;

import java.util.function.BiPredicate;

import org.apache.camel.Component;
import org.apache.camel.Ordered;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.ThrowingBiConsumer;
import org.apache.camel.util.function.ThrowingConsumer;

/**
 * To apply custom configurations to {@link Language} instances.
 */
@FunctionalInterface
public interface LanguageCustomizer extends Ordered {
    /**
     * Create a generic {@link Builder}.
     *
     * @return the {@link Builder}
     */
    static Builder<Language> builder() {
        return builder(Language.class);
    }

    /**
     * Create a typed {@link Builder} that can process a concrete language type instance.
     *
     * @param  type the concrete type of the {@link Component}
     * @return      the {@link ComponentCustomizer.Builder}
     */
    static <T extends Language> Builder<T> builder(Class<T> type) {
        return new Builder<>(type);
    }

    /**
     * Create a {@link DataFormatCustomizer} that can process a concrete language type instance.
     *
     * @param  type     the concrete type of the {@link Language}
     * @param  consumer the {@link Language} configuration logic
     * @return          the {@link LanguageCustomizer}
     */
    static <T extends Language> LanguageCustomizer forType(Class<T> type, ThrowingConsumer<T, Exception> consumer) {
        return builder(type).build(consumer);
    }

    /**
     * Customize the specified {@link Language}.
     *
     * @param name   the unique name of the language
     * @param target the language to configure
     */
    void configure(String name, Language target);

    /**
     * Checks whether this customizer should be applied to the given {@link Language}.
     *
     * @param  name   the unique name of the language
     * @param  target the language to configure
     * @return        <tt>true</tt> if the customizer should be applied
     */
    default boolean isEnabled(String name, Language target) {
        return true;
    }

    @Override
    default int getOrder() {
        return 0;
    }

    // ***************************************
    //
    // Filter
    //
    // ***************************************

    /**
     * Used as additional filer mechanism to control if customizers need to be applied or not. Each of this filter is
     * applied before any of the discovered {@link LanguageCustomizer} is invoked.
     * </p>
     * This interface is useful to implement enable/disable logic on a group of customizers.
     */
    @FunctionalInterface
    interface Policy extends BiPredicate<String, Language> {
        /**
         * A simple deny-all policy.
         *
         * @return false
         */
        static Policy none() {
            return new Policy() {
                @Override
                public boolean test(String s, Language target) {
                    return false;
                }
            };
        }

        /**
         * A simple allow-all policy.
         *
         * @return true
         */
        static Policy any() {
            return new Policy() {
                @Override
                public boolean test(String s, Language target) {
                    return true;
                }
            };
        }
    }

    // ***************************************
    //
    // Builders
    //
    // ***************************************

    /**
     * A fluent builder to create a {@link LanguageCustomizer} instance.
     *
     * @param <T> the concrete type of the {@link Language}.
     */
    class Builder<T extends Language> {
        private final Class<T> type;
        private BiPredicate<String, Language> condition;
        private int order;

        public Builder(Class<T> type) {
            this.type = type;
        }

        public Builder<T> withOrder(int order) {
            this.order = order;

            return this;
        }

        public Builder<T> withCondition(BiPredicate<String, Language> condition) {
            this.condition = condition;

            return this;
        }

        public LanguageCustomizer build(ThrowingConsumer<T, Exception> consumer) {
            return build(new ThrowingBiConsumer<>() {
                @Override
                public void accept(String name, T target) throws Exception {
                    consumer.accept(target);
                }
            });
        }

        public LanguageCustomizer build(ThrowingBiConsumer<String, T, Exception> consumer) {
            final int order = this.order;
            final BiPredicate<String, Language> condition = this.condition != null ? this.condition : (name, target) -> true;

            return new LanguageCustomizer() {
                @SuppressWarnings("unchecked")
                @Override
                public void configure(String name, Language target) {
                    ObjectHelper.notNull(name, "language name");
                    ObjectHelper.notNull(target, "language instance");

                    try {
                        consumer.accept(name, (T) target);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public boolean isEnabled(String name, Language target) {
                    ObjectHelper.notNull(name, "language name");
                    ObjectHelper.notNull(target, "language instance");

                    return type.isAssignableFrom(target.getClass()) && condition.test(name, target);
                }

                @Override
                public int getOrder() {
                    return order;
                }
            };
        }
    }
}
