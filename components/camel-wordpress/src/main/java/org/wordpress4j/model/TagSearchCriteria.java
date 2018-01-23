package org.wordpress4j.model;

import java.util.List;

public class TagSearchCriteria extends ClassifierSearchCriteria {

    private static final long serialVersionUID = 3602397960341909720L;
    
    private List<Integer> offset;
    private TagOrderBy orderBy;

    public TagSearchCriteria() {

    }

    public List<Integer> getOffset() {
        return offset;
    }

    public void setOffset(List<Integer> offset) {
        this.offset = offset;
    }

    public TagOrderBy getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(TagOrderBy orderBy) {
        this.orderBy = orderBy;
    }

}
