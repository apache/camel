package org.wordpress4j.model;

public abstract class ClassifierSearchCriteria extends SearchCriteria {

    private static final long serialVersionUID = -4265001661257396589L;
    
    private boolean hideEmpty;
    private Integer postId;
    private String slug;
    private Context context;

    public ClassifierSearchCriteria() {

    }

    public boolean isHideEmpty() {
        return hideEmpty;
    }

    public void setHideEmpty(boolean hideEmpty) {
        this.hideEmpty = hideEmpty;
    }

    public Integer getPostId() {
        return postId;
    }

    public void setPostId(Integer postId) {
        this.postId = postId;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

}
