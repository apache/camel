package org.wordpress4j.model;

import static com.google.common.base.MoreObjects.toStringHelper;

import java.io.Serializable;
import java.util.List;

public class SearchCriteria implements Serializable {

    private static final long serialVersionUID = 1002576245120313648L;
    
    private Integer page;
    private Integer perPage;
    private String search;
    private Order order;
    private List<Integer> exclude;
    private List<Integer> include;

    public SearchCriteria() {

    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPerPage() {
        return perPage;
    }

    public void setPerPage(Integer perPage) {
        this.perPage = perPage;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public List<Integer> getExclude() {
        return exclude;
    }

    public void setExclude(List<Integer> exclude) {
        this.exclude = exclude;
    }

    public List<Integer> getInclude() {
        return include;
    }

    public void setInclude(List<Integer> include) {
        this.include = include;
    }

    @Override
    public String toString() {
        //@formatter:off
        return toStringHelper(this)
            .add("Query", this.search)
            .add("Page", page)
            .add("Per Page", perPage)
            .addValue(this.order).toString();
    }

}
