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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.util.ObjectHelper;

/**
 * Payload and response for the SObject tree Composite API. The main interface for specifying what to include in the
 * sumission to the API endpoint. To build the tree out use: <blockquote>
 *
 * <pre>
 * {@code
 * Account account = ...
 * Contact president = ...
 * Contact marketing = ...
 *
 * Account anotherAccount = ...
 * Contact sales = ...
 * Asset someAsset = ...
 *
 * SObjectTree request = new SObjectTree();
 * request.addObject(account).addChildren(president, marketing);
 * request.addObject(anotherAccount).addChild(sales).addChild(someAsset);
 * }
 * </pre>
 *
 * </blockquote>
 *
 * This will generate a tree of SObjects resembling: <blockquote>
 *
 * <pre>
 * .
 * |-- account
 * |   |-- president
 * |   `-- marketing
 * `-- anotherAccount
 *     `-- sales
 *         `-- someAsset
 * </pre>
 *
 * </blockquote>
 *
 * By default references that correlate between SObjects in the tree and returned identifiers and errors are handled
 * automatically, if you wish to customize the generation of the reference implement {@link ReferenceGenerator} and
 * supply it as constructor argument to {@link #SObjectTree(ReferenceGenerator)}.
 * <p/>
 * Note that the tree can hold single object type at the root of the tree.
 *
 * @see ReferenceGenerator
 * @see SObjectNode
 * @see AbstractSObjectBase
 * @see AbstractDescribedSObjectBase
 */
@XStreamAlias("SObjectTreeRequest")
public final class SObjectTree implements Serializable {

    private static final long serialVersionUID = 1L;

    @XStreamImplicit
    @JsonProperty
    final List<SObjectNode> records = new CopyOnWriteArrayList<>();

    @XStreamOmitField
    final ReferenceGenerator referenceGenerator;

    @XStreamOmitField
    private String objectType;

    /**
     * Create new SObject tree with the default {@link ReferenceGenerator}.
     */
    public SObjectTree() {
        this(new Counter());
    }

    /**
     * Create new SObject tree with custom {@link ReferenceGenerator}.
     */
    public SObjectTree(final ReferenceGenerator referenceGenerator) {
        this.referenceGenerator = requireNonNull(referenceGenerator,
            "You must specify the ReferenceGenerator implementation");
    }

    /**
     * Add SObject at the root of the tree.
     *
     * @param object
     *            SObject to add
     * @return {@link SObjectNode} for the given SObject
     */
    public SObjectNode addObject(final AbstractSObjectBase object) {
        ObjectHelper.notNull(object, "object");

        return addNode(new SObjectNode(this, object));
    }

    /**
     * Returns a stream of all nodes in the tree.
     *
     * @return
     */
    public Stream<SObjectNode> allNodes() {
        return records.stream().flatMap(r -> Stream.concat(Stream.of(r), r.getChildNodes()));
    }

    /**
     * Returns a stream of all objects in the tree.
     *
     * @return
     */
    public Stream<AbstractSObjectBase> allObjects() {
        return records.stream().flatMap(r -> Stream.concat(Stream.of(r.getObject()), r.getChildren()));
    }

    /**
     * Returns the type of the objects in the root of the tree.
     *
     * @return object type
     */
    @JsonIgnore
    public String getObjectType() {
        return objectType;
    }

    public Class[] objectTypes() {
        final Set<Class> types = records.stream().flatMap(n -> n.objectTypes()).collect(Collectors.toSet());

        return types.toArray(new Class[types.size()]);
    }

    /**
     * Sets errors for the given reference. Used when processing the response of API invocation.
     *
     * @param referenceId
     *            reference identifier
     * @param errors
     *            list of {@link RestError}
     */
    public void setErrorFor(final String referenceId, final List<RestError> errors) {
        for (final SObjectNode node : records) {
            if (setErrorFor(node, referenceId, errors)) {
                return;
            }
        }
    }

    /**
     * Sets identifier of SObject for the given reference. Used when processing the response of API invocation.
     *
     * @param referenceId
     *            reference identifier
     * @param id
     *            SObject identifier
     */
    public void setIdFor(final String referenceId, final String id) {
        for (final SObjectNode node : records) {
            if (setIdFor(node, referenceId, id)) {
                return;
            }
        }
    }

    /**
     * Returns the number of elements in the tree.
     *
     * @return number of elements in the tree
     */
    public int size() {
        return records.stream().mapToInt(r -> r.size()).sum();
    }

    SObjectNode addNode(final SObjectNode node) {
        final String givenObjectType = node.getObjectType();

        if (objectType != null && !objectType.equals(givenObjectType)) {
            throw new IllegalArgumentException("SObjectTree can hold only records of the same type, previously given: "
                + objectType + ", and now trying to add: " + givenObjectType);
        }
        objectType = givenObjectType;

        records.add(node);

        return node;
    }

    boolean setErrorFor(final SObjectNode node, final String referenceId, final List<RestError> errors) {
        final Attributes attributes = node.getAttributes();

        final String attributesReferenceId = attributes.getReferenceId();

        if (Objects.equals(attributesReferenceId, referenceId)) {
            node.setErrors(errors);
            return true;
        }

        return StreamSupport.stream(node.getChildNodes().spliterator(), false)
            .anyMatch(n -> setErrorFor(n, referenceId, errors));
    }

    boolean setIdFor(final SObjectNode node, final String referenceId, final String id) {
        final Attributes attributes = node.getAttributes();

        final String attributesReferenceId = attributes.getReferenceId();

        if (Objects.equals(attributesReferenceId, referenceId)) {
            final Object object = node.getObject();

            if (object != null) {
                return updateBaseObjectId(id, (AbstractSObjectBase) object);
            } else {
                return updateGeneralObjectId(id, object);
            }
        }

        return StreamSupport.stream(node.getChildNodes().spliterator(), false)
            .anyMatch(n -> setIdFor(n, referenceId, id));
    }

    boolean updateBaseObjectId(final String id, final AbstractSObjectBase object) {
        object.setId(id);

        return true;
    }

    boolean updateGeneralObjectId(final String id, final Object object) {
        final Class<? extends Object> clazz = object.getClass();
        final BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(clazz);
        } catch (final IntrospectionException e) {
            throw new IllegalStateException(e);
        }

        final PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();

        final Optional<PropertyDescriptor> maybeIdProperty = Arrays.stream(propertyDescriptors)
            .filter(pd -> "id".equals(pd.getName())).findFirst();

        if (maybeIdProperty.isPresent()) {
            final Method readMethod = maybeIdProperty.get().getReadMethod();
            try {
                readMethod.invoke(object, id);

                return true;
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        }

        return false;
    }
}
