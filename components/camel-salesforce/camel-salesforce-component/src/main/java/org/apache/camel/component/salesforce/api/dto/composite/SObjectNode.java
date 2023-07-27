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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.util.ObjectHelper;

import static java.util.Objects.requireNonNull;

/**
 * Represents one node in the SObject tree request. SObject trees ({@link SObjectTree}) are composed from instances of
 * {@link SObjectNode}s. Each {@link SObjectNode} contains the SObject ({@link AbstractSObjectBase}) and any child
 * records linked to it. SObjects at root level are added to {@link SObjectTree} using
 * {@link SObjectTree#addObject(AbstractSObjectBase)}, then you can add child records on the {@link SObjectNode}
 * returned by using {@link #addChild(AbstractDescribedSObjectBase)},
 * {@link #addChildren(AbstractDescribedSObjectBase, AbstractDescribedSObjectBase...)} or
 * {@link #addChild(String, AbstractSObjectBase)} and
 * {@link #addChildren(String, AbstractSObjectBase, AbstractSObjectBase...)}.
 * <p/>
 * Upon submission to the Salesforce Composite API the {@link SObjectTree} and the {@link SObjectNode}s in it might
 * contain errors that you need to fetch using {@link #getErrors()} method.
 *
 * @see SObjectTree
 * @see RestError
 */
public final class SObjectNode implements Serializable {

    private static final String CHILD_PARAM = "child";

    private static final String SOBJECT_TYPE_PARAM = "type";

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonUnwrapped
    final AbstractSObjectBase object;

    final Map<String, List<SObjectNode>> records = new HashMap<>();

    private List<RestError> errors;

    private final ReferenceGenerator referenceGenerator;

    SObjectNode(final SObjectTree tree, final AbstractSObjectBase object) {
        this(tree.referenceGenerator, object);
    }

    private SObjectNode(final ReferenceGenerator referenceGenerator, final AbstractSObjectBase object) {
        this.referenceGenerator = requireNonNull(referenceGenerator, "ReferenceGenerator cannot be null");
        this.object = requireNonNull(object, "Root SObject cannot be null");
        object.getAttributes().setReferenceId(referenceGenerator.nextReferenceFor(object));
    }

    static String pluralOf(final AbstractDescribedSObjectBase object) {
        final SObjectDescription description = object.description();

        return description.getLabelPlural();
    }

    static String typeOf(final AbstractDescribedSObjectBase object) {
        final SObjectDescription description = object.description();
        return description.getName();
    }

    static String typeOf(final AbstractSObjectBase object) {
        return object.getClass().getSimpleName();
    }

    /**
     * Add a described child with the metadata needed already present within it to the this node.
     *
     * @param  child to add
     * @return       the newly created node, used in builder fashion to add more child objects to it (on the next level)
     */
    public SObjectNode addChild(final AbstractDescribedSObjectBase child) {
        ObjectHelper.notNull(child, CHILD_PARAM);

        return addChild(pluralOf(child), child);
    }

    /**
     * Add a child that does not contain the required metadata to the this node. You need to specify the plural form of
     * the child (e.g. `Account` its `Accounts`).
     *
     * @param  labelPlural plural form
     * @param  child       to add
     * @return             the newly created node, used in builder fashion to add more child objects to it (on the next
     *                     level)
     */
    public SObjectNode addChild(final String labelPlural, final AbstractSObjectBase child) {
        ObjectHelper.notNull(labelPlural, "labelPlural");
        ObjectHelper.notNull(child, CHILD_PARAM);

        final SObjectNode node = new SObjectNode(referenceGenerator, child);

        return addChild(labelPlural, node);
    }

    /**
     * Add multiple described children with the metadata needed already present within them to the this node..
     *
     * @param first  first child to add
     * @param others any other children to add
     */
    public void addChildren(final AbstractDescribedSObjectBase first, final AbstractDescribedSObjectBase... others) {
        ObjectHelper.notNull(first, "first");
        ObjectHelper.notNull(others, "others");

        addChild(pluralOf(first), first);

        Arrays.stream(others).forEach(this::addChild);
    }

    /**
     * Add a child that does not contain the required metadata to the this node. You need to specify the plural form of
     * the child (e.g. `Account` its `Accounts`).
     *
     * @param labelPlural plural form
     * @param first       first child to add
     * @param others      any other children to add
     */
    public void addChildren(final String labelPlural, final AbstractSObjectBase first, final AbstractSObjectBase... others) {
        ObjectHelper.notNull(labelPlural, "labelPlural");
        ObjectHelper.notNull(first, "first");
        ObjectHelper.notNull(others, "others");

        addChild(labelPlural, first);

        Arrays.stream(others).forEach(c -> addChild(labelPlural, c));
    }

    /**
     * Returns all children of this node (one level deep).
     *
     * @return children of this node
     */
    @JsonIgnore
    public Stream<SObjectNode> getChildNodes() {
        return records.values().stream().flatMap(List::stream);
    }

    /**
     * Returns all children of this node (one level deep) of certain type (in plural form).
     *
     * @param  type type of child requested in plural form (e.g for `Account` is `Accounts`)
     * @return      children of this node of specified type
     */
    public Stream<SObjectNode> getChildNodesOfType(final String type) {
        ObjectHelper.notNull(type, SOBJECT_TYPE_PARAM);

        return records.getOrDefault(type, Collections.emptyList()).stream();
    }

    /**
     * Returns child SObjects of this node (one level deep).
     *
     * @return child SObjects of this node
     */
    @JsonIgnore
    public Stream<AbstractSObjectBase> getChildren() {
        return records.values().stream().flatMap(List::stream).map(SObjectNode::getObject);
    }

    /**
     * Returns child SObjects of this node (one level deep) of certain type (in plural form)
     *
     * @param  type type of child requested in plural form (e.g for `Account` is `Accounts`)
     * @return      child SObjects of this node
     */
    public Stream<AbstractSObjectBase> getChildrenOfType(final String type) {
        ObjectHelper.notNull(type, SOBJECT_TYPE_PARAM);

        return records.getOrDefault(type, Collections.emptyList()).stream().map(SObjectNode::getObject);
    }

    /**
     * Errors reported against this this node received in response to the SObject tree being submitted.
     *
     * @return errors for this node
     */
    @JsonIgnore
    public List<RestError> getErrors() {
        return Optional.ofNullable(errors).orElse(Collections.emptyList());
    }

    /**
     * SObject at this node.
     *
     * @return SObject
     */
    @JsonIgnore
    public AbstractSObjectBase getObject() {
        return object;
    }

    /**
     * Are there any errors resulted from the submission on this node?
     *
     * @return true if there are errors
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    /**
     * Size of the branch beginning with this node (number of SObjects in it).
     *
     * @return number of objects within this branch
     */
    public int size() {
        return 1 + records.values().stream().flatMapToInt(r -> r.stream().mapToInt(SObjectNode::size)).sum();
    }

    @Override
    public String toString() {
        return "Node<" + getObjectType() + ">";
    }

    SObjectNode addChild(final String labelPlural, final SObjectNode node) {
        List<SObjectNode> children = records.computeIfAbsent(labelPlural, k -> new ArrayList<>());

        children.add(node);

        return node;
    }

    @JsonAnyGetter
    Map<String, Map<String, List<SObjectNode>>> children() {
        return records.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Collections.singletonMap("records", e.getValue())));
    }

    @JsonIgnore
    String getObjectType() {
        return object.getAttributes().getType();
    }

    Stream<Class<?>> objectTypes() {
        return Stream.concat(Stream.of((Class<?>) object.getClass()), getChildNodes().flatMap(SObjectNode::objectTypes));
    }

    void setErrors(final List<RestError> errors) {
        this.errors = errors;
    }
}
