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
package org.foo.salesforce.contact;

import org.apache.camel.component.connector.DefaultConnectorComponent;

/**
 * Camel salesforce-upsert-contact connector
 */
public class SalesforceUpsertContactComponent extends DefaultConnectorComponent {

    public SalesforceUpsertContactComponent() {
        super("salesforce-upsert-contact", SalesforceUpsertContactComponent.class.getName());
    }

    public SalesforceUpsertContactComponent(String componentScheme) {
        super("salesforce-upsert-contact", componentScheme, SalesforceUpsertContactComponent.class.getName());
    }

}
