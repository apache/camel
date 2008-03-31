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
package org.apache.camel.component.uface;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collection;
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DelegateLifecycleStrategy;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.databinding.observable.list.IObservableList;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.core.databinding.observable.value.WritableValue;
import org.ufacekit.model.ModelHelper;
import org.ufacekit.ui.AttributeDescriptor;
import org.ufacekit.ui.UIComposite;
import org.ufacekit.ui.UIFactory;
import org.ufacekit.ui.beanform.BeanForm;
import org.ufacekit.ui.controls.CellLabelProvider;
import org.ufacekit.ui.controls.UITable;
import org.ufacekit.ui.controls.UITableColumn;
import org.ufacekit.ui.layouts.GridLayoutData;

/**
 * @version $Revision$
 */
public class UFaceBrowser {
    private final DefaultCamelContext camelContext;
    private IObservableList endpoints;
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public UFaceBrowser(DefaultCamelContext camelContext) {
        ObjectHelper.notNull(camelContext, "camelContext");

        this.camelContext = camelContext;
        this.endpoints = new WritableList(Realm.getDefault());

        // lets add any endpoints already added
        List<BrowsableEndpoint> list = CamelContextHelper.getSingletonEndpoints(camelContext, BrowsableEndpoint.class);
        for (BrowsableEndpoint endpoint : list) {
            if (!endpoints.contains(endpoint)) {
                endpoints.add(endpoint);
            }
        }

        camelContext.setLifecycleStrategy(new DelegateLifecycleStrategy(camelContext.getLifecycleStrategy()) {
            @Override
            public void onEndpointAdd(Endpoint<? extends Exchange> endpoint) {
                super.onEndpointAdd(endpoint);

                if (endpoint instanceof BrowsableEndpoint) {
                    onBrowsableEndpoint((BrowsableEndpoint) endpoint);
                }
            }
        });
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public IObservableList getEndpoints() {
        return endpoints;
    }

    public DefaultCamelContext getCamelContext() {
        return camelContext;
    }

    public void createBrowserUI(UIComposite root) {
        UIFactory ui = root.getFactory();
        UIComposite splitter = ui.newHorizontalSplitPanel(root, new GridLayoutData(GridLayoutData.ALIGN_CENTER, GridLayoutData.ALIGN_CENTER));

        UITable tree = createEndpointTableUI(splitter);

        BeanForm selectionForm = new BeanForm();

        UITable table = ui.newTable(splitter, new UITable.TableUIInfo(new GridLayoutData(GridLayoutData.ALIGN_FILL, GridLayoutData.ALIGN_FILL, true, true)));
        selectionForm.add(table, new UITable.TableBindingInfo(selectionForm.detailList("exchanges", Collection.class)));

        ui.newTableColumn(table, new UITableColumn.TableColumnUIInfo(null, new CellLabelProvider() {
            public String getLabel(Object object) {
                Exchange exchange = (Exchange) object;
                return exchange.getIn().getBody(String.class);
            }
        }));
        ui.newTableColumn(table, new UITableColumn.TableColumnUIInfo(null, new CellLabelProvider() {
            public String getLabel(Object object) {
                Exchange exchange = (Exchange) object;
                return exchange.getIn().getHeaders().toString();
            }
        }));

        selectionForm.bind(tree.getSelectionObservable());
    }

    protected UITable createEndpointTableUI(UIComposite root) {
        BeanForm form = new BeanForm();

        UIFactory ui = root.getFactory();
        AttributeDescriptor bindingData = form.detailList("endpoints", Collection.class);

        UITable table = ui.newTable(root, new UITable.TableUIInfo(null));
        form.add(table, new UITable.TableBindingInfo(bindingData));

        ui.newTableColumn(table, new UITableColumn.TableColumnUIInfo(null, new CellLabelProvider() {
            public String getLabel(Object object) {
                BrowsableEndpoint endpoint = (BrowsableEndpoint) object;
                return endpoint.getEndpointUri();
            }
        }));

        WritableValue value = ModelHelper.createWritableValue(this);
        form.bind(value);
        return table;
    }

    protected void onBrowsableEndpoint(BrowsableEndpoint endpoint) {
        endpoints.add(endpoint);
    }
}
