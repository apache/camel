package org.apache.camel.dataformat.bindy.model.simple.spanLastRecord;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

@CsvRecord(separator = ",", autospanLine = true)
public class SpanLastRecord {

    @DataField(pos = 1)
    private int recordId;
    @DataField(pos = 2)
    private String name;
    @DataField(pos = 3)
    private String comment;

    public int getRecordId() {
        return recordId;
    }

    public void setRecordId(final int recordId) {
        this.recordId = recordId;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    @Override
    public String toString() {
        return "SpanLastRecord [recordId=" + recordId + ", name=" + name + ", comment=" + comment + "]";
    }

}
