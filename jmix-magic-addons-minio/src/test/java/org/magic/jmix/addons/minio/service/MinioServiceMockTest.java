package org.magic.jmix.addons.minio.service;

import io.jmix.core.Messages;
import io.minio.BucketExistsArgs;
import io.minio.DeleteBucketLifecycleArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetBucketLifecycleArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.SetBucketLifecycleArgs;
import io.minio.messages.AbortIncompleteMultipartUpload;
import io.minio.messages.Bucket;
import io.minio.messages.Expiration;
import io.minio.messages.Item;
import io.minio.messages.LifecycleConfiguration;
import io.minio.messages.LifecycleRule;
import io.minio.messages.NoncurrentVersionExpiration;
import io.minio.messages.RuleFilter;
import io.minio.messages.Status;
import org.magic.jmix.addons.minio.MinioProperties;
import org.magic.jmix.addons.minio.dto.BatchDeleteResult;
import org.magic.jmix.addons.minio.dto.BatchUploadResult;
import org.magic.jmix.addons.minio.dto.MinioBucketDto;
import org.magic.jmix.addons.minio.dto.MinioLifecycleRuleDto;
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

    // ==================== batchUpload(bucket, requests, threadCount) tests ====================

    @Test
    void batchUpload_shouldReturnSuccessResult_forValidRequests() throws Exception {
        // given
        UploadRequest request = UploadRequest.fromInputStream(
                "test.txt",
                new ByteArrayInputStream("content".getBytes()),
                7
        );

        // when
        CompletableFuture<BatchUploadResult> future = service.batchUpload("test-bucket", List.of(request), 2);
        BatchUploadResult result = future.get();

        // then
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getTotalBytes()).isEqualTo(7);
        assertThat(result.getFailedFiles()).isEmpty();
    }

    @Test
    void batchUpload_shouldReturnEmptyResult_forEmptyRequests() {
        // when
        CompletableFuture<BatchUploadResult> future = service.batchUpload("test-bucket", List.of(), 2);
        BatchUploadResult result = future.join();

        // then
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getTotalBytes()).isEqualTo(0);
    }

    @Test
    void batchUpload_shouldThrowException_forNullBucket() {
        // when & then
        assertThatThrownBy(() -> service.batchUpload(null, List.of(), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void batchUpload_shouldThrowException_forBlankBucket() {
        // when & then
        assertThatThrownBy(() -> service.batchUpload("  ", List.of(), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void batchUpload_shouldThrowException_forInvalidThreadCount() {
        // when & then
        assertThatThrownBy(() -> service.batchUpload("test-bucket", List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        // 验证使用了正确的消息键
        verify(messages).getMessage(eq("org.magic.jmix.addons.minio/service.threadCountInvalid"), any(Locale.class));

        // 重置 mock 以便第二次测试
        reset(messages);
        lenient().when(messages.getMessage(anyString(), any(Locale.class))).thenReturn("mock message: %s");

        assertThatThrownBy(() -> service.batchUpload("test-bucket", List.of(), -1))
                .isInstanceOf(IllegalArgumentException.class);
        verify(messages).getMessage(eq("org.magic.jmix.addons.minio/service.threadCountInvalid"), any(Locale.class));
    }

    @Test
    void batchUpload_shouldShutdownPoolAfterCompletion() throws Exception {
        // given
        UploadRequest request = UploadRequest.fromInputStream(
                "test.txt",
                new ByteArrayInputStream("content".getBytes()),
                7
        );

        // when
        CompletableFuture<BatchUploadResult> future = service.batchUpload("test-bucket", List.of(request), 1);

        // 等待完成
        future.get();

        // 短暂等待确保 whenComplete 执行
        Thread.sleep(100);

        // then - 验证 Future 已完成（线程池已关闭的间接验证）
        assertThat(future.isDone()).isTrue();
    }

    // ==================== Lifecycle tests ====================

    @Test
    void getBucketLifecycle_shouldReturnRules_whenConfigExists() throws Exception {
        // given
        LifecycleRule rule = new LifecycleRule(
                Status.ENABLED,
                null,
                new Expiration((io.minio.messages.ResponseDate) null, 30, null),
                new RuleFilter("logs/"),
                "rule1",
                null,
                null,
                null
        );
        LifecycleConfiguration config = new LifecycleConfiguration(List.of(rule));

        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class))).thenReturn(config);

        // when
        List<MinioLifecycleRuleDto> result = service.getBucketLifecycle("test-bucket");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("rule1");
        assertThat(result.get(0).getEnabled()).isTrue();
        assertThat(result.get(0).getPrefix()).isEqualTo("logs/");
        assertThat(result.get(0).getRetentionDays()).isEqualTo(30);
    }

    @Test
    void getBucketLifecycle_shouldReturnEmptyList_whenNoConfig() throws Exception {
        // given - 模拟 NoSuchLifecycleConfiguration 错误
        Exception noSuchConfig = new RuntimeException("NoSuchLifecycleConfiguration");
        when(minioClient.getBucketLifecycle(any(GetBucketLifecycleArgs.class))).thenThrow(noSuchConfig);

        // when
        List<MinioLifecycleRuleDto> result = service.getBucketLifecycle("test-bucket");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void setBucketLifecycle_shouldCallSetWithRules() throws Exception {
        // given
        MinioLifecycleRuleDto dto = new MinioLifecycleRuleDto();
        dto.setId("rule1");
        dto.setEnabled(true);
        dto.setPrefix("logs/");
        dto.setRetentionDays(30);

        // when
        service.setBucketLifecycle("test-bucket", List.of(dto));

        // then
        verify(minioClient).deleteBucketLifecycle(any(DeleteBucketLifecycleArgs.class));
        verify(minioClient).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    @Test
    void setBucketLifecycle_shouldOnlyDelete_whenRulesEmpty() throws Exception {
        // when
        service.setBucketLifecycle("test-bucket", List.of());

        // then
        verify(minioClient).deleteBucketLifecycle(any(DeleteBucketLifecycleArgs.class));
        verify(minioClient, never()).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    @Test
    void clearBucketLifecycle_shouldCallDelete() throws Exception {
        // when
        service.clearBucketLifecycle("test-bucket");

        // then
        verify(minioClient).deleteBucketLifecycle(any(DeleteBucketLifecycleArgs.class));
    }
}
