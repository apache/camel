package org.apache.camel.component.salesforce.dto.generated;

import com.thoughtworks.xstream.annotations.XStreamImplicit;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;

import java.util.List;

public class QueryRecordsContact extends AbstractQueryRecordsBase {
    @XStreamImplicit
    private List<Contact> records;

    public List<Contact> getRecords() {
        return records;
    }

    public void setRecords(List<Contact> records) {
        this.records = records;
    }
}
