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

package org.apache.camel.kamelet;

import org.apache.camel.builder.KameletRouteTemplate;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.spi.KameletIcon;
import org.apache.camel.spi.KameletProperty;
import org.apache.camel.spi.KameletSpec;

@KameletSpec(type = KameletSpec.KameletType.ACTION,
             name = "set-body-action",
             title = "Set Body Action",
             description = "Sets a simple language parsed value as the new message body in transit.",
             properties = {
                     @KameletProperty(
                                      required = true,
                                      name = "value",
                                      title = "Value",
                                      description = "The value to set as new body.")
             },
             namespace = "Transformation",
             dependencies = { "camel:core", "camel:kamelet" })
@KameletIcon(data = "data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9Im5vIj8+CjxzdmcKICAgeG1sbnM6ZGM9Imh0dHA6Ly9wdXJsLm9yZy9kYy9lbGVtZW50cy8xLjEvIgogICB4bWxuczpjYz0iaHR0cDovL2NyZWF0aXZlY29tbW9ucy5vcmcvbnMjIgogICB4bWxuczpyZGY9Imh0dHA6Ly93d3cudzMub3JnLzE5OTkvMDIvMjItcmRmLXN5bnRheC1ucyMiCiAgIHhtbG5zOnN2Zz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciCiAgIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIKICAgeG1sbnM6c29kaXBvZGk9Imh0dHA6Ly9zb2RpcG9kaS5zb3VyY2Vmb3JnZS5uZXQvRFREL3NvZGlwb2RpLTAuZHRkIgogICB4bWxuczppbmtzY2FwZT0iaHR0cDovL3d3dy5pbmtzY2FwZS5vcmcvbmFtZXNwYWNlcy9pbmtzY2FwZSIKICAgdmlld0JveD0iMCAtMjU2IDE3OTIgMTc5MiIKICAgaWQ9InN2ZzMwMjUiCiAgIHZlcnNpb249IjEuMSIKICAgaW5rc2NhcGU6dmVyc2lvbj0iMC40OC4zLjEgcjk4ODYiCiAgIHdpZHRoPSIxMDAlIgogICBoZWlnaHQ9IjEwMCUiCiAgIHNvZGlwb2RpOmRvY25hbWU9ImNvZ19mb250X2F3ZXNvbWUuc3ZnIj4KICA8bWV0YWRhdGEKICAgICBpZD0ibWV0YWRhdGEzMDM1Ij4KICAgIDxyZGY6UkRGPgogICAgICA8Y2M6V29yawogICAgICAgICByZGY6YWJvdXQ9IiI+CiAgICAgICAgPGRjOmZvcm1hdD5pbWFnZS9zdmcreG1sPC9kYzpmb3JtYXQ+CiAgICAgICAgPGRjOnR5cGUKICAgICAgICAgICByZGY6cmVzb3VyY2U9Imh0dHA6Ly9wdXJsLm9yZy9kYy9kY21pdHlwZS9TdGlsbEltYWdlIiAvPgogICAgICA8L2NjOldvcms+CiAgICA8L3JkZjpSREY+CiAgPC9tZXRhZGF0YT4KICA8ZGVmcwogICAgIGlkPSJkZWZzMzAzMyIgLz4KICA8c29kaXBvZGk6bmFtZWR2aWV3CiAgICAgcGFnZWNvbG9yPSIjZmZmZmZmIgogICAgIGJvcmRlcmNvbG9yPSIjNjY2NjY2IgogICAgIGJvcmRlcm9wYWNpdHk9IjEiCiAgICAgb2JqZWN0dG9sZXJhbmNlPSIxMCIKICAgICBncmlkdG9sZXJhbmNlPSIxMCIKICAgICBndWlkZXRvbGVyYW5jZT0iMTAiCiAgICAgaW5rc2NhcGU6cGFnZW9wYWNpdHk9IjAiCiAgICAgaW5rc2NhcGU6cGFnZXNoYWRvdz0iMiIKICAgICBpbmtzY2FwZTp3aW5kb3ctd2lkdGg9IjY0MCIKICAgICBpbmtzY2FwZTp3aW5kb3ctaGVpZ2h0PSI0ODAiCiAgICAgaWQ9Im5hbWVkdmlldzMwMzEiCiAgICAgc2hvd2dyaWQ9ImZhbHNlIgogICAgIGlua3NjYXBlOnpvb209IjAuMTMxNjk2NDMiCiAgICAgaW5rc2NhcGU6Y3g9Ijg5NiIKICAgICBpbmtzY2FwZTpjeT0iODk2IgogICAgIGlua3NjYXBlOndpbmRvdy14PSIwIgogICAgIGlua3NjYXBlOndpbmRvdy15PSIyNSIKICAgICBpbmtzY2FwZTp3aW5kb3ctbWF4aW1pemVkPSIwIgogICAgIGlua3NjYXBlOmN1cnJlbnQtbGF5ZXI9InN2ZzMwMjUiIC8+CiAgPGcKICAgICB0cmFuc2Zvcm09Im1hdHJpeCgxLDAsMCwtMSwxMjEuNDkxNTMsMTI4NS40MjM3KSIKICAgICBpZD0iZzMwMjciPgogICAgPHBhdGgKICAgICAgIGQ9Im0gMTAyNCw2NDAgcSAwLDEwNiAtNzUsMTgxIC03NSw3NSAtMTgxLDc1IC0xMDYsMCAtMTgxLC03NSAtNzUsLTc1IC03NSwtMTgxIDAsLTEwNiA3NSwtMTgxIDc1LC03NSAxODEsLTc1IDEwNiwwIDE4MSw3NSA3NSw3NSA3NSwxODEgeiBtIDUxMiwxMDkgViA1MjcgcSAwLC0xMiAtOCwtMjMgLTgsLTExIC0yMCwtMTMgbCAtMTg1LC0yOCBxIC0xOSwtNTQgLTM5LC05MSAzNSwtNTAgMTA3LC0xMzggMTAsLTEyIDEwLC0yNSAwLC0xMyAtOSwtMjMgLTI3LC0zNyAtOTksLTEwOCAtNzIsLTcxIC05NCwtNzEgLTEyLDAgLTI2LDkgbCAtMTM4LDEwOCBxIC00NCwtMjMgLTkxLC0zOCAtMTYsLTEzNiAtMjksLTE4NiAtNywtMjggLTM2LC0yOCBIIDY1NyBxIC0xNCwwIC0yNC41LDguNSBRIDYyMiwtMTExIDYyMSwtOTggTCA1OTMsODYgcSAtNDksMTYgLTkwLDM3IEwgMzYyLDE2IFEgMzUyLDcgMzM3LDcgMzIzLDcgMzEyLDE4IDE4NiwxMzIgMTQ3LDE4NiBxIC03LDEwIC03LDIzIDAsMTIgOCwyMyAxNSwyMSA1MSw2Ni41IDM2LDQ1LjUgNTQsNzAuNSAtMjcsNTAgLTQxLDk5IEwgMjksNDk1IFEgMTYsNDk3IDgsNTA3LjUgMCw1MTggMCw1MzEgdiAyMjIgcSAwLDEyIDgsMjMgOCwxMSAxOSwxMyBsIDE4NiwyOCBxIDE0LDQ2IDM5LDkyIC00MCw1NyAtMTA3LDEzOCAtMTAsMTIgLTEwLDI0IDAsMTAgOSwyMyAyNiwzNiA5OC41LDEwNy41IDcyLjUsNzEuNSA5NC41LDcxLjUgMTMsMCAyNiwtMTAgbCAxMzgsLTEwNyBxIDQ0LDIzIDkxLDM4IDE2LDEzNiAyOSwxODYgNywyOCAzNiwyOCBoIDIyMiBxIDE0LDAgMjQuNSwtOC41IFEgOTE0LDEzOTEgOTE1LDEzNzggbCAyOCwtMTg0IHEgNDksLTE2IDkwLC0zNyBsIDE0MiwxMDcgcSA5LDkgMjQsOSAxMywwIDI1LC0xMCAxMjksLTExOSAxNjUsLTE3MCA3LC04IDcsLTIyIDAsLTEyIC04LC0yMyAtMTUsLTIxIC01MSwtNjYuNSAtMzYsLTQ1LjUgLTU0LC03MC41IDI2LC01MCA0MSwtOTggbCAxODMsLTI4IHEgMTMsLTIgMjEsLTEyLjUgOCwtMTAuNSA4LC0yMy41IHoiCiAgICAgICBpZD0icGF0aDMwMjkiCiAgICAgICBpbmtzY2FwZTpjb25uZWN0b3ItY3VydmF0dXJlPSIwIgogICAgICAgc3R5bGU9ImZpbGw6Y3VycmVudENvbG9yIiAvPgogIDwvZz4KPC9zdmc+Cg==")
public class SetBodyAction extends KameletRouteTemplate {

    @Override
    protected void template(RouteTemplateDefinition template) {
        template.from(kameletSource())
                .setBody().simple("{{value}}");
    }
}
