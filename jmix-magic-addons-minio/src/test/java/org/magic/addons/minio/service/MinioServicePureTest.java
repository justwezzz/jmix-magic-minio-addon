package org.magic.addons.minio.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinioServicePureTest {

    private final MinioService service = new MinioService(null, null, null);

    @Test
    void isPlaceholder_shouldReturnTrue_forPlaceholderFile() {
        assertThat(service.isPlaceholder("folder/.minio_placeholder")).isTrue();
        assertThat(service.isPlaceholder("folder/subfolder/.minio_placeholder")).isTrue();
    }

    @Test
    void isPlaceholder_shouldReturnFalse_forNormalFile() {
        assertThat(service.isPlaceholder("folder/file.txt")).isFalse();
        assertThat(service.isPlaceholder("file.txt")).isFalse();
        assertThat(service.isPlaceholder(".minio_placeholder")).isFalse();  // 没有 / 前缀
    }

    @Test
    void isPlaceholder_shouldReturnFalse_forNull() {
        assertThat(service.isPlaceholder(null)).isFalse();
    }

    // ==================== extractFileName tests ====================

    @Test
    void extractFileName_shouldExtract_fromPath() {
        assertThat(service.extractFileName("folder/subfolder/file.txt")).isEqualTo("file.txt");
        assertThat(service.extractFileName("folder/file.txt")).isEqualTo("file.txt");
    }

    @Test
    void extractFileName_shouldReturn_asIs_forNoPath() {
        assertThat(service.extractFileName("file.txt")).isEqualTo("file.txt");
    }

    @Test
    void extractFileName_shouldReturnEmpty_forNull() {
        assertThat(service.extractFileName(null)).isEmpty();
    }

    @Test
    void extractFileName_shouldReturnEmpty_forEmptyString() {
        assertThat(service.extractFileName("")).isEmpty();
    }

    // ==================== extractParentPath tests ====================

    @Test
    void extractParentPath_shouldExtract_fromMultiLevelPath() {
        assertThat(service.extractParentPath("folder/subfolder/file.txt")).isEqualTo("folder/subfolder/");
    }

    @Test
    void extractParentPath_shouldReturnEmpty_forRootLevelFile() {
        assertThat(service.extractParentPath("file.txt")).isEmpty();
    }

    @Test
    void extractParentPath_shouldHandle_folderPath() {
        assertThat(service.extractParentPath("folder/subfolder/")).isEqualTo("folder/");
    }

    @Test
    void extractParentPath_shouldReturnEmpty_forNull() {
        assertThat(service.extractParentPath(null)).isEmpty();
    }

    @Test
    void extractParentPath_shouldReturnEmpty_forEmptyString() {
        assertThat(service.extractParentPath("")).isEmpty();
    }

    // ==================== formatSize tests ====================

    @Test
    void formatSize_shouldFormatBytes() {
        assertThat(service.formatSize(0L)).isEqualTo("0 B");
        assertThat(service.formatSize(500L)).isEqualTo("500 B");
        assertThat(service.formatSize(1023L)).isEqualTo("1023 B");
    }

    @Test
    void formatSize_shouldFormatKilobytes() {
        assertThat(service.formatSize(1024L)).isEqualTo("1.0 KB");
        assertThat(service.formatSize(1536L)).isEqualTo("1.5 KB");
    }

    @Test
    void formatSize_shouldFormatMegabytes() {
        assertThat(service.formatSize(1048576L)).isEqualTo("1.0 MB");
        assertThat(service.formatSize(1572864L)).isEqualTo("1.5 MB");
    }

    @Test
    void formatSize_shouldFormatGigabytes() {
        assertThat(service.formatSize(1073741824L)).isEqualTo("1.0 GB");
    }

    @Test
    void formatSize_shouldHandleNegativeAndNull() {
        assertThat(service.formatSize(-1L)).isEqualTo("-");
        assertThat(service.formatSize(null)).isEqualTo("-");
    }
}
