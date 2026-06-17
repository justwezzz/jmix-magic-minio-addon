package org.magic.jmix.addons.minio.dto;

import java.util.List;

public class PagedSearchResult {

    private List<MinioTreeNode> items;       // 当前页结果
    private String nextCursor;               // 下一页游标（null 表示没有更多）
    private boolean hasMore;                 // 是否有更多结果
    private int totalFetched;                // 已获取总数

    public PagedSearchResult() {
    }

    public List<MinioTreeNode> getItems() {
        return items;
    }

    public void setItems(List<MinioTreeNode> items) {
        this.items = items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public int getTotalFetched() {
        return totalFetched;
    }

    public void setTotalFetched(int totalFetched) {
        this.totalFetched = totalFetched;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<MinioTreeNode> items;
        private String nextCursor;
        private boolean hasMore;
        private int totalFetched;

        public Builder items(List<MinioTreeNode> items) {
            this.items = items;
            return this;
        }

        public Builder nextCursor(String nextCursor) {
            this.nextCursor = nextCursor;
            return this;
        }

        public Builder hasMore(boolean hasMore) {
            this.hasMore = hasMore;
            return this;
        }

        public Builder totalFetched(int totalFetched) {
            this.totalFetched = totalFetched;
            return this;
        }

        public PagedSearchResult build() {
            PagedSearchResult result = new PagedSearchResult();
            result.items = this.items;
            result.nextCursor = this.nextCursor;
            result.hasMore = this.hasMore;
            result.totalFetched = this.totalFetched;
            return result;
        }
    }
}
