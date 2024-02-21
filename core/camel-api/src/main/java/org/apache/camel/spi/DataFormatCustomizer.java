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
 * To apply custom configurations to {@link DataFormat} instances.
 */
@FunctionalInterface
public interface DataFormatCustomizer extends Ordered {
    /**
     * Create a generic {@link Builder}.
     *
     * @return the {@link Builder}
     */
    static Builder<DataFormat> builder() {
        return builder(DataFormat.class);
    }

    /**
     * Create a typed {@link Builder} that can process a concrete data format type instance.
     *
     * @param  type the concrete type of the {@link Component}
     * @return      the {@link ComponentCustomizer.Builder}
     */
    static <T extends DataFormat> Builder<T> builder(Class<T> type) {
        return new Builder<>(type);
    }

    /**
     * Create a {@link DataFormatCustomizer} that can process a concrete data format type instance.
     *
     * @param  type     the concrete type of the {@link DataFormat}
     * @param  consumer the {@link DataFormat} configuration logic
     * @return          the {@link DataFormatCustomizer}
     */
    static <T extends DataFormat> DataFormatCustomizer forType(Class<T> type, ThrowingConsumer<T, Exception> consumer) {
        return builder(type).build(consumer);
    }

    /**
     * Customize the specified {@link DataFormat}.
     *
     * @param name   the unique name of the data format
     * @param target the data format to configure
     */
    void configure(String name, DataFormat target);

    /**
     * Checks whether this customizer should be applied to the given {@link DataFormat}.
     *
     * @param  name   the unique name of the data format
     * @param  target the data format to configure
     * @return        <tt>true</tt> if the customizer should be applied
     */
    default boolean isEnabled(String name, DataFormat target) {
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
     * applied before any of the discovered {@link DataFormatCustomizer} is invoked.
     * </p>
     * This interface is useful to implement enable/disable logic on a group of customizers.
     */
    @FunctionalInterface
    interface Policy extends BiPredicate<String, DataFormat> {
        /**
         * A simple deny-all policy.
         *
         * @return false
         */
        static Policy none() {
            return new Policy() {
                @Override
                public boolean test(String s, DataFormat target) {
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
                public boolean test(String s, DataFormat target) {
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
     * A fluent builder to create a {@link DataFormatCustomizer} instance.
     *
     * @param <T> the concrete type of the {@link DataFormat}.
     */
    class Builder<T extends DataFormat> {
        private final Class<T> type;
        private BiPredicate<String, DataFormat> condition;
        private int order;

        public Builder(Class<T> type) {
            this.type = type;
        }

        public Builder<T> withOrder(int order) {
            this.order = order;

            return this;
        }

        public Builder<T> withCondition(BiPredicate<String, DataFormat> condition) {
            this.condition = condition;

            return this;
        }

        public DataFormatCustomizer build(ThrowingConsumer<T, Exception> consumer) {
            return build(new ThrowingBiConsumer<String, T, Exception>() {
                @Override
                public void accept(String name, T target) throws Exception {
                    consumer.accept(target);
                }
            });
        }

        public DataFormatCustomizer build(ThrowingBiConsumer<String, T, Exception> consumer) {
            final int order = this.order;
            final BiPredicate<String, DataFormat> condition = condition();

            return new DataFormatCustomizer() {
                @SuppressWarnings("unchecked")
                @Override
                public void configure(String name, DataFormat target) {
                    ObjectHelper.notNull(name, "data format name");
                    ObjectHelper.notNull(target, "data format instance");

                    try {
                        consumer.accept(name, (T) target);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public boolean isEnabled(String name, DataFormat target) {
                    ObjectHelper.notNull(name, "data format name");
                    ObjectHelper.notNull(target, "data format instance");

                    return condition.test(name, target);
                }

                @Override
                public int getOrder() {
                    return order;
                }
            };
        }

        private BiPredicate<String, DataFormat> condition() {
            if (type.equals(DataFormat.class)) {
                return this.condition != null
                        ? this.condition
                        : new BiPredicate<>() {
                            @Override
                            public boolean test(String s, DataFormat language) {
                                return true;
                            }
                        };
            }

            if (condition == null) {
                return new BiPredicate<>() {
                    @Override
                    public boolean test(String name, DataFormat target) {
                        return type.isAssignableFrom(target.getClass());
                    }
                };
            }

            return new BiPredicate<>() {
                @Override
                public boolean test(String name, DataFormat target) {
                    return type.isAssignableFrom(target.getClass()) && condition.test(name, target);
                }
            };
        }
    }
}
