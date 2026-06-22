package org.magic.jmix.addons.minio.service;

import io.jmix.core.Messages;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import org.magic.jmix.addons.minio.MinioProperties;
import org.magic.jmix.addons.minio.dto.BatchDeleteResult;
import org.magic.jmix.addons.minio.dto.BatchUploadResult;
import org.magic.jmix.addons.minio.dto.MinioBucketDto;
import org.magic.jmix.addons.minio.dto.MinioTreeNode;
import org.magic.jmix.addons.minio.dto.NodeType;
import org.magic.jmix.addons.minio.dto.UploadRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioServiceMockTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private Messages messages;

    @Mock
    private MinioProperties properties;

    @Mock
    private MinioProperties.Upload uploadProperties;

    @Mock
    private ExecutorService uploadThreadPool;

    private MinioService service;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(properties.getUpload()).thenReturn(uploadProperties);
        // batchSize 已从 Upload 配置中移除

        // 简化 mock：返回带占位符的通用消息，避免 String.format NPE
        lenient().when(messages.getMessage(anyString(), any(Locale.class))).thenReturn("mock message: %s");
        lenient().when(messages.getMessage(anyString())).thenReturn("mock message");

        // Mock MinioProperties 的连接配置，与 injectMockClient 中的缓存值一致
        lenient().when(properties.getEndpoint()).thenReturn("http://localhost:9000");
        lenient().when(properties.getAccessKey()).thenReturn("minioadmin");
        lenient().when(properties.getSecretKey()).thenReturn("minioadmin");

        service = new MinioService(properties, messages, uploadThreadPool);

        // 通过反射注入 mock 的 minioClient
        injectMockClient();
    }

    private void injectMockClient() throws Exception {
        injectMockClient(service);
    }

    private void injectMockClient(MinioService targetService) throws Exception {
        Field clientField = MinioService.class.getDeclaredField("cachedClient");
        clientField.setAccessible(true);
        clientField.set(targetService, minioClient);

        Field endpointField = MinioService.class.getDeclaredField("cachedEndpoint");
        endpointField.setAccessible(true);
        endpointField.set(targetService, "http://localhost:9000");

        Field accessKeyField = MinioService.class.getDeclaredField("cachedAccessKey");
        accessKeyField.setAccessible(true);
        accessKeyField.set(targetService, "minioadmin");

        Field secretKeyField = MinioService.class.getDeclaredField("cachedSecretKey");
        secretKeyField.setAccessible(true);
        secretKeyField.set(targetService, "minioadmin");
    }

    // ==================== listBuckets tests ====================

    @Test
    void listBuckets_shouldReturnDtoList() throws Exception {
        // given
        Bucket bucket = mock(Bucket.class);
        when(bucket.name()).thenReturn("test-bucket");
        when(bucket.creationDate()).thenReturn(ZonedDateTime.now());
        when(minioClient.listBuckets()).thenReturn(List.of(bucket));

        // when
        List<MinioBucketDto> result = service.listBuckets();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("test-bucket");
    }

    @Test
    void listBuckets_shouldReturnEmptyList_whenNoBuckets() throws Exception {
        // given
        when(minioClient.listBuckets()).thenReturn(List.of());

        // when
        List<MinioBucketDto> result = service.listBuckets();

        // then
        assertThat(result).isEmpty();
    }

    // ==================== createBucket tests ====================

    @Test
    void createBucket_shouldCallMakeBucket_whenNotExists() throws Exception {
        // given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        // when
        service.createBucket("new-bucket");

        // then
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void createBucket_shouldThrowException_whenAlreadyExists() throws Exception {
        // given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> service.createBucket("existing-bucket"))
                .isInstanceOf(IllegalArgumentException.class);

        // 验证使用了正确的消息键
        verify(messages).getMessage(eq("org.magic.jmix.addons.minio/service.bucketNameExists"), any(Locale.class));
    }

    // ==================== bucketExists tests ====================

    @Test
    void bucketExists_shouldReturnTrue_whenBucketExists() throws Exception {
        // given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // when
        boolean result = service.bucketExists("test-bucket");

        // then
        assertThat(result).isTrue();
    }

    @Test
    void bucketExists_shouldReturnFalse_whenBucketNotExists() throws Exception {
        // given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        // when
        boolean result = service.bucketExists("test-bucket");

        // then
        assertThat(result).isFalse();
    }

    // ==================== deleteBucket tests ====================

    @Test
    void deleteBucket_shouldDeleteAllObjectsAndBucket() throws Exception {
        // given
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn("file.txt");

        io.minio.Result<Item> result = mock(io.minio.Result.class);
        when(result.get()).thenReturn(item);

        when(minioClient.listObjects(any(ListObjectsArgs.class)))
                .thenReturn(List.of(result));

        // when
        service.deleteBucket("test-bucket");

        // then
        verify(minioClient).removeObjects(any(RemoveObjectsArgs.class));
        verify(minioClient).removeBucket(any(RemoveBucketArgs.class));
    }

    // ==================== uploadFile tests ====================

    @Test
    void uploadFile_shouldCallPutObject() throws Exception {
        // given
        InputStream stream = new ByteArrayInputStream("test content".getBytes());

        // when
        service.uploadFile("test-bucket", "test.txt", stream, 12);

        // then
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_shouldThrowException_forPlaceholderFile() {
        // when & then
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        assertThatThrownBy(() -> service.uploadFile("bucket", "folder/.minio_placeholder", stream, 0))
                .isInstanceOf(IllegalArgumentException.class);

        // 验证使用了正确的消息键
        verify(messages).getMessage(eq("org.magic.jmix.addons.minio/service.reservedFilename"), any(Locale.class));
    }

    // ==================== downloadFile tests ====================

    @Test
    void downloadFile_shouldReturnInputStream() throws Exception {
        // given
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

        // when
        InputStream result = service.downloadFile("test-bucket", "test.txt");

        // then
        assertThat(result).isNotNull();
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    // ==================== deleteFile tests ====================

    @Test
    void deleteFile_shouldCallRemoveObject() throws Exception {
        // when
        service.deleteFile("test-bucket", "test.txt");

        // then
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    // ==================== batchUploadAsync tests ====================

    @Test
    void batchUploadAsync_shouldReturnSuccessResult_forValidRequests() throws Exception {
        // given - 使用真实线程池执行
        ExecutorService realThreadPool = Executors.newFixedThreadPool(2);
        MinioService realService = new MinioService(properties, messages, realThreadPool);
        injectMockClient(realService);

        UploadRequest request = UploadRequest.fromInputStream(
                "test.txt",
                new ByteArrayInputStream("content".getBytes()),
                7
        );

        // when
        CompletableFuture<BatchUploadResult> future = realService.batchUploadAsync("test-bucket", List.of(request));
        BatchUploadResult result = future.get();

        // then
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getTotalBytes()).isEqualTo(7);
        assertThat(result.getFailedFiles()).isEmpty();

        realThreadPool.shutdown();
    }

    @Test
    void batchUploadAsync_shouldReturnEmptyResult_forEmptyRequests() {
        // when
        CompletableFuture<BatchUploadResult> future = service.batchUploadAsync("test-bucket", List.of());
        BatchUploadResult result = future.join();

        // then
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getTotalBytes()).isEqualTo(0);
    }

    @Test
    void batchUploadAsync_shouldThrowException_forNullBucket() {
        // when & then
        assertThatThrownBy(() -> service.batchUploadAsync(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void batchUploadAsync_shouldThrowException_forBlankBucket() {
        // when & then
        assertThatThrownBy(() -> service.batchUploadAsync("  ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== batchDelete tests ====================

    @Test
    void batchDelete_shouldDeleteFilesAndCountCorrectly() throws Exception {
        // given
        MinioTreeNode fileNode = MinioTreeNode.builder()
                .type(NodeType.FILE)
                .name("test.txt")
                .path("test.txt")
                .build();

        // when
        BatchDeleteResult result = service.batchDelete("test-bucket", List.of(fileNode));

        // then
        assertThat(result.getDeletedFiles()).isEqualTo(1);
        assertThat(result.getDeletedFolders()).isEqualTo(0);
        verify(minioClient).removeObjects(any(RemoveObjectsArgs.class));
    }

    @Test
    void batchDelete_shouldHandleFolderDeletion() throws Exception {
        // given
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn("folder/file.txt");

        io.minio.Result<Item> itemResult = mock(io.minio.Result.class);
        when(itemResult.get()).thenReturn(item);

        MinioTreeNode folderNode = MinioTreeNode.builder()
                .type(NodeType.FOLDER)
                .name("folder")
                .path("folder")
                .build();

        when(minioClient.listObjects(any(ListObjectsArgs.class)))
                .thenReturn(List.of(itemResult));

        // when
        BatchDeleteResult deleteResult = service.batchDelete("test-bucket", List.of(folderNode));

        // then
        assertThat(deleteResult.getDeletedFolders()).isEqualTo(1);
        assertThat(deleteResult.getDeletedFiles()).isEqualTo(1);
    }
}
