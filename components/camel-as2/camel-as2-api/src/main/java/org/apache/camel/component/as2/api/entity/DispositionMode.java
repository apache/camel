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
package org.apache.camel.component.as2.api.entity;

public enum DispositionMode {
    MANUAL_ACTION_MDN_SENT_MANUALLY("manual-action", "MDN-sent-manually"),
    MANUAL_ACTION_MDN_SENT_AUTOMATICALLY("manual-action", "MDN-sent-automatically"),
    AUTOMATIC_ACTION_MDN_SENT_MANUALLY("automatic-action", "MDN-sent-manually"),
    AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY("automatic-action", "MDN-sent-automatically");

    private final String actionMode;
    private final String sendingMode;

    private DispositionMode(String actionMode, String sendingMode) {
        this.actionMode = actionMode;
        this.sendingMode = sendingMode;
    }

    public String getActionMode() {
        return actionMode;
    }

    public String getSendingMode() {
        return sendingMode;
    }

    @Override
    public String toString() {
        return actionMode + "/" + sendingMode;
    }

    public static DispositionMode parseDispositionMode(String dispositionModeString) {
        switch (dispositionModeString) {
            case "manual-action/MDN-sent-manually":
                return MANUAL_ACTION_MDN_SENT_MANUALLY;
            case "manual-actionMDN-sent-automatically":
                return MANUAL_ACTION_MDN_SENT_AUTOMATICALLY;
            case "automatic-action/MDN-sent-manually":
                return AUTOMATIC_ACTION_MDN_SENT_MANUALLY;
            case "automatic-action/MDN-sent-automatically":
                return AUTOMATIC_ACTION_MDN_SENT_AUTOMATICALLY;
            default:
                return null;
        }
    }
}
