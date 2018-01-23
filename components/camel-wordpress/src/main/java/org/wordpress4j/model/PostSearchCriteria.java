package org.wordpress4j.model;

import java.util.List;

public class PostSearchCriteria extends PublishableSearchCriteria {

    private static final long serialVersionUID = 2663161640460268421L;
    
    private List<String> categories;
    private List<String> categoriesExclude;
    private List<String> tags;
    private List<String> tagsExclude;
    private Boolean stick;
    private PostOrderBy orderBy;

    public PostOrderBy getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(PostOrderBy orderBy) {
        this.orderBy = orderBy;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public List<String> getCategoriesExclude() {
        return categoriesExclude;
    }

    public void setCategoriesExclude(List<String> categoriesExclude) {
        this.categoriesExclude = categoriesExclude;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getTagsExclude() {
        return tagsExclude;
    }

    public void setTagsExclude(List<String> tagsExclude) {
        this.tagsExclude = tagsExclude;
    }

    public Boolean getStick() {
        return stick;
    }

    public void setStick(Boolean stick) {
        this.stick = stick;
    }

}
