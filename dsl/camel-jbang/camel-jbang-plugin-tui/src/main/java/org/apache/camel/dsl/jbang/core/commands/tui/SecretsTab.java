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

import java.util.ArrayList;
import java.util.List;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class SecretsTab extends AbstractTableTab {

    SecretsTab(MonitorContext ctx) {
        super(ctx, "vault", "secret");
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.vaultSecrets.size() : 0;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<VaultSecretInfo> sorted = new ArrayList<>(info.vaultSecrets);
        sorted.sort(this::sortSecret);

        List<Row> rows = new ArrayList<>();
        for (VaultSecretInfo vi : sorted) {
            rows.add(Row.from(
                    Cell.from(Span.styled(" " + (vi.vault != null ? vi.vault : ""), Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(vi.region != null ? vi.region : ""),
                    Cell.from(vi.secret != null ? vi.secret : ""),
                    Cell.from(printSince(vi.timestamp)),
                    Cell.from(printSince(vi.lastReload)),
                    Cell.from(printSince(vi.lastCheck))));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No secrets", 6));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(" " + sortLabel("VAULT", "vault"), sortStyle("vault"))),
                        Cell.from(Span.styled("REGION", Style.EMPTY.bold())),
                        Cell.from(Span.styled(sortLabel("SECRET", "secret"), sortStyle("secret"))),
                        Cell.from(Span.styled("AGE", Style.EMPTY.bold())),
                        Cell.from(Span.styled("UPDATE", Style.EMPTY.bold())),
                        Cell.from(Span.styled("CHECK", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(16),
                        Constraint.length(16),
                        Constraint.fill(),
                        Constraint.length(10),
                        Constraint.length(10),
                        Constraint.length(10))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Secrets ").build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, sorted.size());
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isChar('f')) {
            IntegrationInfo info = ctx.findSelectedIntegration();
            if (info != null && info.pid != null) {
                JsonObject root = new JsonObject();
                root.put("action", "vault-refresh");
                ctx.fireAction(info.pid, root);
                if (ctx.notificationCallback != null) {
                    ctx.notificationCallback.accept("Secrets reload in progress", false);
                }
            }
            return true;
        }
        return false;
    }

    private int sortSecret(VaultSecretInfo a, VaultSecretInfo b) {
        int result = switch (sort) {
            case "secret" -> {
                String sa = a.secret != null ? a.secret : "";
                String sb = b.secret != null ? b.secret : "";
                yield sa.compareToIgnoreCase(sb);
            }
            default -> { // "vault"
                String va = a.vault != null ? a.vault : "";
                String vb = b.vault != null ? b.vault : "";
                int c = va.compareToIgnoreCase(vb);
                if (c == 0) {
                    String sa = a.secret != null ? a.secret : "";
                    String sb = b.secret != null ? b.secret : "";
                    c = sa.compareToIgnoreCase(sb);
                }
                yield c;
            }
        };
        return sortReversed ? -result : result;
    }

    private static String printSince(long timestamp) {
        if (timestamp == 0) {
            return "";
        }
        return TimeUtils.printSince(timestamp);
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.vaultSecrets.isEmpty()) {
            return null;
        }
        List<VaultSecretInfo> sorted = new ArrayList<>(info.vaultSecrets);
        sorted.sort(this::sortSecret);
        List<String> items = sorted.stream()
                .map(v -> (v.vault != null ? v.vault : "") + "/" + (v.secret != null ? v.secret : ""))
                .toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Secrets");
    }

    @Override
    public String description() {
        return "Secrets from cloud vault providers (AWS, Azure, GCP, Kubernetes, HashiCorp, IBM)";
    }

    @Override
    public String getHelpText() {
        return """
                # Secrets

                Shows secrets resolved from cloud vault providers. Camel can load
                configuration values from external secret managers instead of
                hardcoding them in properties files.

                Supported providers: AWS Secrets Manager, Azure Key Vault,
                GCP Secret Manager, HashiCorp Vault, IBM Secrets Manager,
                Kubernetes Secrets, and Kubernetes ConfigMaps.

                ## Table Columns

                - **VAULT** — The cloud provider name (AWS, Azure, GCP, Hashicorp, IBM, Kubernetes, Kubernetes-cm)
                - **REGION** — Cloud region (AWS only, blank for other providers)
                - **SECRET** — The secret name/key as referenced in the route configuration
                - **AGE** — How long ago the secret value was last updated
                - **UPDATE** — How long ago the last automatic reload of secrets occurred
                - **CHECK** — How long ago the last check for secret changes was performed

                ## Automatic Refresh

                Camel can automatically detect secret changes and reload them:
                - **AWS** — monitors via CloudTrail events
                - **Azure** — monitors via Event Hubs
                - **GCP** — monitors via Pub/Sub
                - **IBM** — monitors via Event Streams (Kafka)
                - **Kubernetes** — watches the Kubernetes API

                When refresh is enabled, the UPDATE and CHECK columns show the
                timing of these automatic operations.

                ## Keys

                - `Up/Down` — select secret
                - `f` — force reload secrets from vault providers
                - `s` — cycle sort column
                - `S` — reverse sort order
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Secrets");
        JsonArray rows = new JsonArray();
        List<VaultSecretInfo> sorted = new ArrayList<>(info.vaultSecrets);
        sorted.sort(this::sortSecret);
        for (VaultSecretInfo vi : sorted) {
            JsonObject row = new JsonObject();
            row.put("vault", vi.vault);
            row.put("region", vi.region);
            row.put("secret", vi.secret);
            row.put("age", printSince(vi.timestamp));
            row.put("update", printSince(vi.lastReload));
            row.put("check", printSince(vi.lastCheck));
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.vaultSecrets.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
