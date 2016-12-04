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
package org.apache.camel.component.salesforce.api.dto.approval;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Represents approval request sent to submit, approve or reject record.
 *
 * @see <a href=
 *      "https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_process_approvals.htm">
 *      https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_process_approvals.htm</a>
 */
@UriParams
@XStreamAlias("requests")
public final class ApprovalRequest implements Serializable {

    public enum Action {
        Submit, Approve, Reject
    }

    /**
     * Lazy holder of fields defined in {@link ApprovalRequest}.
     */
    private static final class FieldHolder {
        public static final FieldHolder INSTANCE = new FieldHolder();

        public final List<Field> fields;

        private FieldHolder() {
            fields = Arrays.stream(ApprovalRequest.class.getDeclaredFields())
                    .filter(f -> !Modifier.isFinal(f.getModifiers())).collect(Collectors.toList());
        }
    }

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    @UriParam
    @Metadata(required = "true")
    private Action actionType;

    @UriParam
    @Metadata(required = "true")
    private String contextActorId;

    @UriParam
    @Metadata(required = "true")
    private String contextId;

    @UriParam
    private String comments;

    @UriParam
    @Metadata(required = "true")
    @XStreamImplicit(itemFieldName = "nextApproverIds")
    private List<String> nextApproverIds;

    @UriParam
    @Metadata(required = "true")
    private String processDefinitionNameOrId;

    @UriParam
    private boolean skipEntryCriteria;

    public void addNextApproverId(final String nextApproverId) {
        nextApproverIds = Optional.ofNullable(nextApproverIds).orElse(new ArrayList<>());
        nextApproverIds.add(nextApproverId);
    }

    /**
     * Creates new {@link ApprovalRequest} by combining values from the given template with the values currently
     * present. If the value is not present and the template has the corresponding value, then the template value is
     * set. The net result is that all set values of an {@link ApprovalRequest} are preserved, while the values set on
     * template are used for undefined ( <code>null</code>) values.
     *
     * @param template
     *            template to apply
     * @return newly created object with applied template
     */
    public ApprovalRequest applyTemplate(final ApprovalRequest template) {
        if (template == null) {
            return this;
        }

        final ApprovalRequest withTemplateValues = new ApprovalRequest();

        for (final Field field : FieldHolder.INSTANCE.fields) {
            try {
                final Object currentValue = field.get(this);

                // if a field has not been set, and the template has it set use
                // the template value
                if (currentValue == null) {
                    final Object templateValue = field.get(template);

                    if (templateValue != null) {
                        field.set(withTemplateValues, templateValue);
                    }
                } else {
                    field.set(withTemplateValues, currentValue);
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new IllegalStateException("Unable to apply values from template", e);
            }
        }

        return withTemplateValues;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof ApprovalRequest)) {
            return false;
        }

        final ApprovalRequest other = (ApprovalRequest) obj;

        return Objects.equals(actionType, other.actionType) && Objects.equals(contextActorId, other.contextActorId)
            && Objects.equals(contextId, other.contextId) && Objects.equals(comments, other.comments)
            && Objects.equals(nextApproverIds, other.nextApproverIds)
            && Objects.equals(processDefinitionNameOrId, other.processDefinitionNameOrId)
            && Objects.equals(skipEntryCriteria, other.skipEntryCriteria);
    }

    public Action getActionType() {
        return actionType;
    }

    public String getComments() {
        return comments;
    }

    public String getContextActorId() {
        return contextActorId;
    }

    public String getContextId() {
        return contextId;
    }

    public List<String> getNextApproverIds() {
        return listFromNullable(nextApproverIds);
    }

    public String getProcessDefinitionNameOrId() {
        return processDefinitionNameOrId;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] {actionType, contextActorId, contextId, comments, nextApproverIds,
            processDefinitionNameOrId, skipEntryCriteria});
    }

    public boolean isSkipEntryCriteria() {
        return skipEntryCriteria;
    }

    public void setActionType(final Action actionType) {
        this.actionType = actionType;
    }

    public void setComments(final String comments) {
        this.comments = comments;
    }

    public void setContextActorId(final String contextActorId) {
        this.contextActorId = contextActorId;
    }

    public void setContextId(final String contextId) {
        this.contextId = contextId;
    }

    public void setNextApproverIds(final List<String> nextApproverIds) {
        this.nextApproverIds = new ArrayList<>(listFromNullable(nextApproverIds));
    }

    public void setNextApproverIds(final String nextApproverId) {
        // set single approver id
        this.nextApproverIds = Collections.singletonList(nextApproverId);
    }

    public void setProcessDefinitionNameOrId(final String processDefinitionNameOrId) {
        this.processDefinitionNameOrId = processDefinitionNameOrId;
    }

    public void setSkipEntryCriteria(final boolean skipEntryCriteria) {
        this.skipEntryCriteria = skipEntryCriteria;
    }

    @Override
    public String toString() {
        final StringBuilder buildy = new StringBuilder("ApprovalRequest: ");
        buildy.append("actionType: ").append(actionType);
        buildy.append(", contextActorId: ").append(contextActorId);
        buildy.append(", contextId: ").append(contextId);
        buildy.append(", comments: ").append(comments);
        buildy.append(", nextApproverIds: ").append(nextApproverIds);
        buildy.append(", processDefinitionNameOrId: ").append(processDefinitionNameOrId);
        buildy.append(", skipEntryCriteria: ").append(skipEntryCriteria);

        return buildy.toString();
    }

    private List<String> listFromNullable(final List<String> nullable) {
        return Optional.ofNullable(nullable).orElse(Collections.emptyList());
    }
}
