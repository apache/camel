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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;

/** Switches the AI panel to another provider and model, or an ACP agent, for the session. */
final class AiProviderSwitchPopup extends ChoicePopup<AiProviderSwitchPopup.ProviderChoice> {

    record ProviderChoice(String provider, String model, String url, boolean persistedDefault) {
        ProviderChoice {
            if (provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("provider must not be blank");
            }
        }

        String label() {
            if (AiProviderSelector.isAcp(provider)) {
                return AiProviderSelector.acpLabel(provider) + (persistedDefault ? "  default" : "");
            }
            String modelLabel = model == null || model.isBlank() ? "auto" : model;
            return provider + "  " + modelLabel + (persistedDefault ? "  default" : "");
        }
    }

    void open(List<ProviderChoice> choices) {
        show(choices);
    }

    @Override
    protected String title() {
        return " AI Provider ";
    }

    @Override
    protected String label(ProviderChoice choice) {
        return choice.label();
    }

    @Override
    protected String confirmLabel() {
        return "select";
    }
}
