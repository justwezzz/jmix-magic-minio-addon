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
public class BatchDeleteResult {
    private int deletedFiles;
    private int deletedFolders;
    private List<String> failedItems;
    private List<String> errors;
}
