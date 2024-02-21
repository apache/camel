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
 * To apply custom configurations to {@link Component} instances.
 */
@FunctionalInterface
public interface ComponentCustomizer extends Ordered {
    /**
     * Create a generic {@link Builder}.
     *
     * @return the {@link Builder}
     */
    static Builder<Component> builder() {
        return builder(Component.class);
    }

    /**
     * Create a typed {@link Builder} that can process a concrete component type instance.
     *
     * @param  type the concrete type of the {@link Component}
     * @return      the {@link Builder}
     */
    static <T extends Component> Builder<T> builder(Class<T> type) {
        return new Builder<>(type);
    }

    /**
     * Create a {@link ComponentCustomizer} that can process a concrete component type instance.
     *
     * @param  type     the concrete type of the {@link Component}
     * @param  consumer the {@link Component} configuration logic
     * @return          the {@link ComponentCustomizer}
     */
    static <T extends Component> ComponentCustomizer forType(Class<T> type, ThrowingConsumer<T, Exception> consumer) {
        return builder(type).build(consumer);
    }

    /**
     * Customize the specified {@link Component}.
     *
     * @param name   the unique name of the component
     * @param target the component to configure
     */
    void configure(String name, Component target);

    /**
     * Checks whether this customizer should be applied to the given {@link Component}.
     *
     * @param  name   the unique name of the component
     * @param  target the component to configure
     * @return        <tt>true</tt> if the customizer should be applied
     */
    default boolean isEnabled(String name, Component target) {
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
     * applied before any of the discovered {@link ComponentCustomizer} is invoked.
     * </p>
     * This interface is useful to implement enable/disable logic on a group of customizers.
     */
    @FunctionalInterface
    interface Policy extends BiPredicate<String, Component> {
        /**
         * A simple deny-all policy.
         *
         * @return false
         */
        static Policy none() {
            return new Policy() {
                @Override
                public boolean test(String s, Component target) {
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
                public boolean test(String s, Component target) {
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
     * A fluent builder to create a {@link ComponentCustomizer} instance.
     *
     * @param <T> the concrete type of the {@link Component}.
     */
    class Builder<T extends Component> {
        private final Class<T> type;
        private BiPredicate<String, Component> condition;
        private int order;

        public Builder(Class<T> type) {
            this.type = type;
        }

        public Builder<T> withOrder(int order) {
            this.order = order;

            return this;
        }

        public Builder<T> withCondition(BiPredicate<String, Component> condition) {
            this.condition = condition;

            return this;
        }

        public ComponentCustomizer build(ThrowingConsumer<T, Exception> consumer) {
            return build(new ThrowingBiConsumer<String, T, Exception>() {
                @Override
                public void accept(String name, T target) throws Exception {
                    consumer.accept(target);
                }
            });
        }

        public ComponentCustomizer build(ThrowingBiConsumer<String, T, Exception> consumer) {
            final int order = this.order;
            final BiPredicate<String, Component> condition = condition();

            return new ComponentCustomizer() {
                @SuppressWarnings("unchecked")
                @Override
                public void configure(String name, Component target) {
                    ObjectHelper.notNull(name, "component name");
                    ObjectHelper.notNull(target, "component instance");

                    try {
                        consumer.accept(name, (T) target);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public boolean isEnabled(String name, Component target) {
                    ObjectHelper.notNull(name, "component name");
                    ObjectHelper.notNull(target, "component instance");

                    return condition.test(name, target);
                }

                @Override
                public int getOrder() {
                    return order;
                }
            };
        }

        private BiPredicate<String, Component> condition() {
            if (type.equals(Component.class)) {
                return this.condition != null
                        ? this.condition
                        : new BiPredicate<>() {
                            @Override
                            public boolean test(String s, Component language) {
                                return true;
                            }
                        };
            }

            if (condition == null) {
                return new BiPredicate<>() {
                    @Override
                    public boolean test(String name, Component target) {
                        return type.isAssignableFrom(target.getClass());
                    }
                };
            }

            return new BiPredicate<>() {
                @Override
                public boolean test(String name, Component target) {
                    return type.isAssignableFrom(target.getClass()) && condition.test(name, target);
                }
            };
        }
    }
}
