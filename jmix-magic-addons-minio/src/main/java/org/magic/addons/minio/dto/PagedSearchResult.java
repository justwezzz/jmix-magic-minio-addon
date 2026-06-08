package org.magic.addons.minio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedSearchResult {
    private List<MinioTreeNode> items;       // 当前页结果
    private String nextCursor;               // 下一页游标（null 表示没有更多）
    private boolean hasMore;                 // 是否有更多结果
    private int totalFetched;                // 已获取总数
}
