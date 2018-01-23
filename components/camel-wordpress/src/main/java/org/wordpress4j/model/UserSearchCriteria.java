package org.wordpress4j.model;

import java.util.List;

public class UserSearchCriteria extends SearchCriteria {

    private static final long serialVersionUID = 6531943297389260204L;

    private Context context;
    private List<Integer> offset;
    private UserOrderBy orderBy;
    private List<String> roles;
    private List<String> slug;
    
    public UserSearchCriteria() {

    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public List<Integer> getOffset() {
        return offset;
    }

    public void setOffset(List<Integer> offset) {
        this.offset = offset;
    }

    public UserOrderBy getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(UserOrderBy orderBy) {
        this.orderBy = orderBy;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getSlug() {
        return slug;
    }

    public void setSlug(List<String> slug) {
        this.slug = slug;
    }
    
}
