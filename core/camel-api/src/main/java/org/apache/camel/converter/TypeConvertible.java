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

package org.apache.camel.converter;

import java.util.Objects;

import static org.apache.camel.util.ObjectHelper.convertPrimitiveTypeToWrapperType;

/**
 * Holds a type convertible pair. That is, consider 2 types defined as F and T, it defines that F can be converted to T.
 *
 * @param <F> The "from" type
 * @param <T> The "to" type.
 */
public final class TypeConvertible<F, T> {
    private final Class<F> from;
    private final Class<T> to;
    private final int hash;

    /**
     * Constructs a new type convertible pair. This is likely only used by core camel code and auto-generated bulk
     * loaders. This is an internal API and not meant for end users.
     *
     * @param from The class instance that defines the "from" type (that is: Class&lt;F&gt;.class). Must NOT be null.
     * @param to   The class instance that defines the "to" type (that is: Class&lt;F&gt;.class). Must NOT be null.
     */
    public TypeConvertible(Class<F> from, Class<T> to) {
        assert from != null;
        assert to != null;

        this.from = from;
        this.to = to;

        this.hash = calculateHash();
    }

    /**
     * Tests whether there is a conversion match from this TypeConvertible to the given TypeConvertible.
     *
     * For instance, consider 2 TypeConvertibles defined as "tc1{from: Number.class, to: String.class}" and "tc2{from:
     * Integer.class, to: String.class}", it traverses the type hierarchy of the "from" class to determine if it, or any
     * of its superclasses or any of its interfaces match with the "from" type of this instance.
     *
     * @param  that the TypeConvertible being tested against this instance
     * @return      true if there is a conversion match between the give TypeConvertible and this instance.
     */
    public boolean matches(TypeConvertible<?, ?> that) {
        return match(this.from, this.to, that.from, that.to);
    }

    /**
     * Tests whether there is a conversion match from this TypeConvertible to the given TypeConvertible when the "to"
     * type of the tested TypeConvertible is a primitive type. See {@link TypeConvertible#matches(TypeConvertible)} for
     * details.
     *
     * @param  that the TypeConvertible being tested against this instance
     * @return      true if there is a conversion match between the give TypeConvertible and this instance.
     */
    public boolean matchesPrimitive(TypeConvertible<?, ?> that) {
        if (that != null && that.getTo() != null) {
            return match(this.from, this.to, that.from, convertPrimitiveTypeToWrapperType(that.to));
        }

        return false;
    }

    /**
     * A recursive implementation of the type match algorithm.
     *
     * @param  thisFrom The class instance that defines the source "from" type (that is: Class&lt;F&gt;.class)
     * @param  thisTo   The class instance that defines the source "to" type (that is: Class&lt;F&gt;.class)
     * @param  thatFrom The class instance that defines the target "from" type (that is: Class&lt;F&gt;.class)
     * @param  thatTo   The class instance that defines the source "to" type (that is: Class&lt;F&gt;.class)
     * @return          true if there is a conversion match between the source types to the target types
     */
    private static boolean match(Class<?> thisFrom, Class<?> thisTo, Class<?> thatFrom, Class<?> thatTo) {
        if (thatFrom == null || thatTo == null) {
            return false;
        }

        // Try direct
        if (directMatch(thisFrom, thisTo, thatFrom, thatTo)) {
            return true;
        }

        /* Try interfaces:
         * Try to resolve a TypeConverter by looking at the interfaces implemented by a given "from" type. It looks at the
         * type hierarchy of the target "from type" trying to match a suitable converter (i.e.: Integer -> Number). It will
         * recursively analyze the whole hierarchy.
         */
        final Class<?>[] interfaceTypes = thatFrom.getInterfaces();
        for (Class<?> interfaceType : interfaceTypes) {
            if (match(thisFrom, thisTo, interfaceType, thatTo)) {
                return true;
            }
        }

        /*
         * Try to resolve a TypeConverter by looking at the parent class of a given "from" type. It looks at the type
         * hierarchy of the "from type" trying to match a suitable converter (i.e.: Integer -> Number). It will recursively
         * analyze the whole hierarchy, and it will also evaluate the interfaces implemented by such type.
         */
        return match(thisFrom, thisTo, thatFrom.getSuperclass(), thatTo);
    }

    /**
     * Tests whether the types defined in this type convertable pair are assignable from another type convertable pair
     *
     * @param  that the type convertible pair to test
     * @return      true if the types in this instance are assignable from the respective types in the given type
     *              convertible
     */
    public boolean isAssignableMatch(TypeConvertible<?, ?> that) {
        return isAssignableMatch(this.from, this.to, that.from, that.to);
    }

    private static boolean isAssignableMatch(Class<?> thisFrom, Class<?> thisTo, Class<?> thatFrom, Class<?> thatTo) {
        if (thisFrom == Object.class) {
            return false;
        }

        if (thisTo.isAssignableFrom(thatTo)) {
            return thisFrom.isAssignableFrom(thatFrom);
        }

        return false;
    }

    /**
     * Try a direct match conversion (i.e.: those which the type conversion pair have a direct entry on the converters)
     *
     * @param  thisFrom The class instance that defines the source "from" type (that is: Class&lt;F&gt;.class)
     * @param  thisTo   The class instance that defines the source "to" type (that is: Class&lt;F&gt;.class)
     * @param  thatFrom The class instance that defines the target "from" type (that is: Class&lt;F&gt;.class)
     * @param  thatTo   The class instance that defines the source "to" type (that is: Class&lt;F&gt;.class)
     * @return          true if the types match directly or false otherwise
     */

    private static boolean directMatch(Class<?> thisFrom, Class<?> thisTo, Class<?> thatFrom, Class<?> thatTo) {
        if (Objects.equals(thisFrom, thatFrom)) {
            return Objects.equals(thisTo, thatTo);
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TypeConvertible<?, ?> that = (TypeConvertible<?, ?>) o;
        return directMatch(this.from, this.to, that.from, that.to);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    private int calculateHash() {
        int result = 1;

        result = 31 * result + from.hashCode();
        result = 31 * result + to.hashCode();

        return result;
    }

    /**
     * Gets the class instance that defines the "from" type (that is: Class&lt;F&gt;.class)
     *
     * @return the "from" class instance
     */
    public Class<F> getFrom() {
        return from;
    }

    /**
     * Gets the class instance that defines the "to" type (that is: Class&lt;F&gt;.class).
     *
     * @return the "to" class instance
     */
    public Class<T> getTo() {
        return to;
    }

    @Override
    public String toString() {
        return "TypeConvertible{" +
               "from=" + from +
               ", to=" + to +
               '}';
    }
}
