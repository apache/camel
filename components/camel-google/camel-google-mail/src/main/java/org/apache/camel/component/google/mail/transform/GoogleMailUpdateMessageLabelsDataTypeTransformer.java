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
package org.apache.camel.component.google.mail.transform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.google.mail.GoogleMailComponent;
import org.apache.camel.component.google.mail.internal.GoogleMailConstants;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Data type transformer that builds a {@link ModifyMessageRequest} that updates labels of a message from exchange
 * variables {@code addLabels} and {@code removeLabels} .
 *
 */
@DataTypeTransformer(name = "google-mail:update-message-labels",
                     description = "Updates Gmail message labels by resolving label names from addLabels/removeLabels exchange variables")
public class GoogleMailUpdateMessageLabelsDataTypeTransformer extends Transformer {

    public static final String VARIABLE_ADD_LABELS = "addLabels";
    public static final String VARIABLE_REMOVE_LABELS = "removeLabels";

    private static final String DEFAULT_USER_ID = "me";
    private static final String HEADER_USER_ID = GoogleMailConstants.PROPERTY_PREFIX + "userId";

    @Override
    public void transform(Message message, DataType fromType, DataType toType) throws Exception {
        Exchange exchange = message.getExchange();

        List<String> addLabelNames = getLabelsVariable(exchange, VARIABLE_ADD_LABELS);
        List<String> removeLabelNames = getLabelsVariable(exchange, VARIABLE_REMOVE_LABELS);

        if (addLabelNames.isEmpty() && removeLabelNames.isEmpty()) {
            throw new CamelExecutionException(
                    "At least one of 'addLabels' or 'removeLabels' exchange variables must be set", exchange);
        }

        String userId = message.getHeader(HEADER_USER_ID, DEFAULT_USER_ID, String.class);
        Map<String, String> labelMap = fetchLabelMap(userId);

        List<String> addLabelIds = resolveLabelIds(addLabelNames, labelMap, exchange);
        List<String> removeLabelIds = resolveLabelIds(removeLabelNames, labelMap, exchange);

        ModifyMessageRequest request = new ModifyMessageRequest();
        if (!addLabelIds.isEmpty()) {
            request.setAddLabelIds(addLabelIds);
        }
        if (!removeLabelIds.isEmpty()) {
            request.setRemoveLabelIds(removeLabelIds);
        }

        message.setBody(request);
    }

    @SuppressWarnings("unchecked")
    private List<String> getLabelsVariable(Exchange exchange, String variableName) {
        Object value = exchange.getVariable(variableName);
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            return (List<String>) value;
        }
        if (value instanceof String stringValue) {
            if (stringValue.isBlank()) {
                return Collections.emptyList();
            }
            return Arrays.stream(stringValue.split(",")).map(String::trim).toList();
        }
        throw new CamelExecutionException(
                "Exchange variable '" + variableName + "' must be a List<String> or a comma-separated String, but was: "
                                          + value.getClass().getName(),
                exchange);
    }

    private List<String> resolveLabelIds(List<String> labelNames, Map<String, String> labelMap, Exchange exchange) {
        List<String> ids = new ArrayList<>(labelNames.size());
        for (String name : labelNames) {
            String id = labelMap.get(name);
            if (id == null) {
                throw new CamelExecutionException(
                        "Label '" + name + "' not found. Available labels: " + labelMap.keySet(), exchange);
            }
            ids.add(id);
        }
        return ids;
    }

    private Map<String, String> fetchLabelMap(String userId) throws Exception {
        Gmail client = getGmailClient();
        ListLabelsResponse response = client.users().labels().list(userId).execute();
        Map<String, String> map = new HashMap<>();
        if (response.getLabels() != null) {
            for (Label label : response.getLabels()) {
                map.put(label.getName(), label.getId());
            }
        }
        return map;
    }

    private Gmail getGmailClient() {
        GoogleMailComponent component = getCamelContext().getComponent("google-mail", GoogleMailComponent.class);
        return component.getClient(component.getConfiguration());
    }
}
