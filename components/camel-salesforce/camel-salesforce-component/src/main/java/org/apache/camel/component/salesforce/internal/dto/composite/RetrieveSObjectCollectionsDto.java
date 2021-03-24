package org.apache.camel.component.salesforce.internal.dto.composite;

import java.io.Serializable;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("batch")
public class RetrieveSObjectCollectionsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<String> ids;
    private List<String> fields;

    public RetrieveSObjectCollectionsDto() {
    }

    public RetrieveSObjectCollectionsDto(List<String> ids, List<String> fields) {
        this.ids = ids;
        this.fields = fields;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }
}
