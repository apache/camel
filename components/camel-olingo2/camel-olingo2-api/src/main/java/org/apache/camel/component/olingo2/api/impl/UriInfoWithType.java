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
package org.apache.camel.component.olingo2.api.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.olingo.odata2.api.commons.InlineCount;
import org.apache.olingo.odata2.api.edm.EdmEntityContainer;
import org.apache.olingo.odata2.api.edm.EdmEntitySet;
import org.apache.olingo.odata2.api.edm.EdmException;
import org.apache.olingo.odata2.api.edm.EdmFunctionImport;
import org.apache.olingo.odata2.api.edm.EdmLiteral;
import org.apache.olingo.odata2.api.edm.EdmMultiplicity;
import org.apache.olingo.odata2.api.edm.EdmProperty;
import org.apache.olingo.odata2.api.edm.EdmType;
import org.apache.olingo.odata2.api.edm.EdmTypeKind;
import org.apache.olingo.odata2.api.exception.ODataApplicationException;
import org.apache.olingo.odata2.api.uri.KeyPredicate;
import org.apache.olingo.odata2.api.uri.NavigationPropertySegment;
import org.apache.olingo.odata2.api.uri.NavigationSegment;
import org.apache.olingo.odata2.api.uri.SelectItem;
import org.apache.olingo.odata2.api.uri.UriInfo;
import org.apache.olingo.odata2.api.uri.expression.FilterExpression;
import org.apache.olingo.odata2.api.uri.expression.OrderByExpression;

/**
 * UriInfo with UriType information, determined in constructor.
 */
public class UriInfoWithType implements UriInfo {

    private final UriInfo uriInfo;
    private final UriType uriType;

    public UriInfoWithType(UriInfo uriInfo, String resourcePath) throws ODataApplicationException, EdmException {
        this.uriInfo = uriInfo;

        // determine Uri Type
        UriType uriType;
        final List<NavigationSegment> segments = uriInfo.getNavigationSegments();
        final boolean isLinks = uriInfo.isLinks();
        if (segments.isEmpty() && uriInfo.getTargetType() == null) {
            uriType = UriType.URI0;
            if (resourcePath.endsWith("$metadata")) {
                uriType = UriType.URI8;
            } else if (resourcePath.endsWith("$batch")) {
                uriType = UriType.URI9;
            }
        } else {
            final EdmEntitySet targetEntitySet = uriInfo.getTargetEntitySet();
            if (targetEntitySet != null) {
                final boolean isCount = uriInfo.isCount();
                final List<KeyPredicate> keyPredicates = uriInfo.getKeyPredicates();
                if (keyPredicates.isEmpty()) {
                    if (!isCount) {
                        uriType = UriType.URI1;
                    } else {
                        uriType = UriType.URI15;
                    }
                } else {
                    uriType = UriType.URI2;
                    if (isCount) {
                        uriType = UriType.URI16;
                    } else if (uriInfo.isValue()) {
                        uriType = UriType.URI17;
                    }
                    final EdmTypeKind targetKind = uriInfo.getTargetType().getKind();
                    switch (targetKind) {
                        case SIMPLE:
                            if (segments.isEmpty()) {
                                uriType = UriType.URI5;
                            } else {
                                uriType = UriType.URI4;
                            }
                            break;
                        case COMPLEX:
                            uriType = UriType.URI3;
                            break;
                        case ENTITY:
                            final List<EdmProperty> propertyPath = uriInfo.getPropertyPath();
                            if (!segments.isEmpty() || !propertyPath.isEmpty()) {
                                boolean many = false;
                                if (!propertyPath.isEmpty()) {
                                    final EdmProperty lastProperty = propertyPath.get(propertyPath.size() - 1);
                                    many = lastProperty.getMultiplicity() == EdmMultiplicity.MANY;
                                } else {
                                    final NavigationSegment lastSegment = segments.get(segments.size() - 1);
                                    many = lastSegment.getKeyPredicates().isEmpty() && lastSegment.getNavigationProperty().getMultiplicity() == EdmMultiplicity.MANY;
                                }
                                if (isCount) {
                                    if (many) {
                                        uriType = isLinks ? UriType.URI50B : UriType.URI15;
                                    } else {
                                        uriType = UriType.URI50A;
                                    }
                                } else {
                                    if (many) {
                                        uriType = isLinks ? UriType.URI7B : UriType.URI6B;
                                    } else {
                                        uriType = isLinks ? UriType.URI7A : UriType.URI6A;
                                    }
                                }
                            }
                            break;
                        default:
                            throw new ODataApplicationException("Unexpected property type " + targetKind, Locale.ENGLISH);
                    }
                }
            } else {
                final EdmFunctionImport functionImport = uriInfo.getFunctionImport();
                final EdmType targetType = uriInfo.getTargetType();

                final boolean isCollection = functionImport.getReturnType().getMultiplicity() == EdmMultiplicity.MANY;
                switch (targetType.getKind()) {
                    case SIMPLE:
                        uriType = isCollection ? UriType.URI13 : UriType.URI14;
                        break;
                    case COMPLEX:
                        uriType = isCollection ? UriType.URI11 : UriType.URI12;
                        break;
                    case ENTITY:
                        uriType = UriType.URI10;
                        break;
                    default:
                        throw new ODataApplicationException("Invalid function return type " + targetType, Locale.ENGLISH);
                }
            }
        }
        this.uriType = uriType;
    }

    public UriType getUriType() {
        return uriType;
    }

    @Override
    public EdmEntityContainer getEntityContainer() {
        return uriInfo.getEntityContainer();
    }

    @Override
    public EdmEntitySet getStartEntitySet() {
        return uriInfo.getStartEntitySet();
    }

    @Override
    public EdmEntitySet getTargetEntitySet() {
        return uriInfo.getTargetEntitySet();
    }

    @Override
    public EdmFunctionImport getFunctionImport() {
        return uriInfo.getFunctionImport();
    }

    @Override
    public EdmType getTargetType() {
        return uriInfo.getTargetType();
    }

    @Override
    public List<KeyPredicate> getKeyPredicates() {
        return uriInfo.getKeyPredicates();
    }

    @Override
    public List<KeyPredicate> getTargetKeyPredicates() {
        return uriInfo.getTargetKeyPredicates();
    }

    @Override
    public List<NavigationSegment> getNavigationSegments() {
        return uriInfo.getNavigationSegments();
    }

    @Override
    public List<EdmProperty> getPropertyPath() {
        return uriInfo.getPropertyPath();
    }

    @Override
    public boolean isCount() {
        return uriInfo.isCount();
    }

    @Override
    public boolean isValue() {
        return uriInfo.isValue();
    }

    @Override
    public boolean isLinks() {
        return uriInfo.isLinks();
    }

    @Override
    public String getFormat() {
        return uriInfo.getFormat();
    }

    @Override
    public FilterExpression getFilter() {
        return uriInfo.getFilter();
    }

    @Override
    public InlineCount getInlineCount() {
        return uriInfo.getInlineCount();
    }

    @Override
    public OrderByExpression getOrderBy() {
        return uriInfo.getOrderBy();
    }

    @Override
    public String getSkipToken() {
        return uriInfo.getSkipToken();
    }

    @Override
    public Integer getSkip() {
        return uriInfo.getSkip();
    }

    @Override
    public Integer getTop() {
        return uriInfo.getTop();
    }

    @Override
    public List<ArrayList<NavigationPropertySegment>> getExpand() {
        return uriInfo.getExpand();
    }

    @Override
    public List<SelectItem> getSelect() {
        return uriInfo.getSelect();
    }

    @Override
    public Map<String, EdmLiteral> getFunctionImportParameters() {
        return uriInfo.getFunctionImportParameters();
    }

    @Override
    public Map<String, String> getCustomQueryOptions() {
        return uriInfo.getCustomQueryOptions();
    }
}
