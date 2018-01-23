package org.wordpress4j.model;

public class CategorySearchCriteria extends ClassifierSearchCriteria {

    private static final long serialVersionUID = 3124924225447605233L;
    
    private CategoryOrderBy orderBy;
    private Integer parent;

    public CategorySearchCriteria() {

    }

    public CategoryOrderBy getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(CategoryOrderBy orderBy) {
        this.orderBy = orderBy;
    }

    public Integer getParent() {
        return parent;
    }

    public void setParent(Integer parent) {
        this.parent = parent;
    }

}
