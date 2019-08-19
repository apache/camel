package org.apache.camel.component.salesforce.dto.generated;

import com.thoughtworks.xstream.annotations.XStreamImplicit;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;

import java.util.List;

public class QueryRecordsAccount extends AbstractQueryRecordsBase {
    @XStreamImplicit
    private List<Account> records;

    public List<Account> getRecords() {
        return records;
    }

    public void setRecords(List<Account> records) {
        this.records = records;
    }
}
