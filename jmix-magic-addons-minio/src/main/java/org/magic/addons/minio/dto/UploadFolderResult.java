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
public class UploadFolderResult {
    private int uploadedFiles;
    private int createdFolders;
    private long totalBytes;
    private List<String> failedFiles;
    private List<String> errors;
}
