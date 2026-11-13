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
package org.apache.camel.component.cedar;

import com.cedarpolicy.value.EntityTypeName;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.ExpressionAdapter;

/** Constructs entity identifiers as data, including arbitrary quotes in a verified workload ID. */
@Metadata(label = "bean",
          description = "Builds a Cedar EntityUID from a configured type and constant ID or exchange property, without constructing policy code.",
          annotations = { "interfaceName=org.apache.camel.Expression" })
@Configurer(metadataOnly = true)
public class CedarEntityUidExpression extends ExpressionAdapter {
    @Metadata(required = true, description = "Cedar entity type name, for example User or App::Document.")
    private String entityType;
    @Metadata(description = "Constant entity identifier. Configure exactly one of id and exchangeProperty.")
    private String id;
    @Metadata(description = "Exchange property containing the entity identifier as a String. Configure exactly one of id and exchangeProperty. A verified identity must be populated by authentication before this expression runs.")
    private String exchangeProperty;
    private EntityTypeName parsedType;

    @Override
    public void init(CamelContext context) {
        super.init(context);
        if (entityType == null || (id == null) == (exchangeProperty == null)) {
            throw new IllegalArgumentException(
                    "CedarEntityUidExpression requires entityType and exactly one of id and exchangeProperty");
        }
        parsedType
                = EntityTypeName.parse(entityType).orElseThrow(() -> new IllegalArgumentException("Invalid Cedar entityType"));
    }

    @Override
    public Object evaluate(Exchange exchange) {
        Object identifier = id != null ? id : exchange.getProperty(exchangeProperty);
        if (!(identifier instanceof String text)) {
            throw new IllegalArgumentException("Missing String Cedar entity identifier");
        }
        return parsedType.of(text);
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExchangeProperty() {
        return exchangeProperty;
    }

    public void setExchangeProperty(String exchangeProperty) {
        this.exchangeProperty = exchangeProperty;
    }
}
