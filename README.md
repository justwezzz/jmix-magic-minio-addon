# Jmix MinIO Addon

涓€涓敤浜?Jmix 2.x 妗嗘灦鐨?MinIO 鏂囦欢瀛樺偍鎻掍欢锛屾彁渚涘畬鏁寸殑鏂囦欢娴忚鍣ㄧ晫闈€?

> 馃摉 **瀹夸富椤圭洰闆嗘垚涓庝娇鐢ㄨ瑙?[USAGE.md](USAGE.md)**

## 鍔熻兘鐗规€?

- **Bucket 绠＄悊**锛氬垱寤恒€佸垹闄ゃ€侀噸鍛藉悕 Bucket
- **鏂囦欢娴忚**锛氭爲褰㈢粨鏋勬噿鍔犺浇娴忚鏂囦欢
- **鏂囦欢棰勮**锛氬弻鍑绘枃鏈?鍥剧墖鍐呰仈棰勮锛圕odeEditor 鍙 / Image锛夛紝PDF/闊宠棰戠敤娴忚鍣ㄥ師鐢熼瑙堬紝鍏朵粬鏍煎紡寮规鎻愮ず涓嬭浇锛汣trl + 鍗曞嚮鏂囦欢鍚嶇敤娴忚鍣ㄦ柊鏍囩鎵撳紑
- **鏂囦欢涓婁紶**锛氭敮鎸佸崟鏂囦欢鍜屾枃浠跺す涓婁紶锛堜繚鐣欑洰褰曠粨鏋勶級锛屽凡姹夊寲
- **鏂囦欢涓嬭浇**锛氬崟鏂囦欢涓嬭浇鍜屽鏂囦欢 ZIP 鎵撳寘涓嬭浇
- **鎼滅储鍔熻兘**锛氭父鏍囧垎椤垫噿鍔犺浇鎼滅储锛堟粴鍔ㄥ埌搴曡嚜鍔ㄥ姞杞斤紝鏂囦欢鍚嶅叧閿瓧楂樹寒锛?
- **鎵归噺鎿嶄綔**锛氭壒閲忓垹闄ゃ€丼hift 鑼冨洿閫夋嫨銆佸彸閿彍鍗?

## 瀹夎

### 1. 娣诲姞渚濊禆

鍦ㄩ」鐩殑 `build.gradle` 涓坊鍔狅細

```groovy
dependencies {
    implementation 'org.magic.addons:jmix-magic-addons-minio-starter:0.0.1'
}
```

### 2. 閰嶇疆 MinIO 杩炴帴

鍦?`application.properties` 涓厤缃細

```properties
# MinIO 杩炴帴锛堝繀濉級
magic.minio.endpoint=http://localhost:9000      # MinIO 鏈嶅姟鍦板潃
magic.minio.access-key=minioadmin               # 璁块棶瀵嗛挜
magic.minio.secret-key=minioadmin               # 绉樺瘑瀵嗛挜

# 涓嬭浇閰嶇疆锛堝彲閫夛級
magic.minio.download.max-files=1000             # ZIP 鎵撳寘涓嬭浇鏈€澶ф枃浠舵暟
magic.minio.download.max-size=100MB             # 鍗曟枃浠朵笅杞芥渶澶уぇ灏?

# 涓婁紶閰嶇疆锛堝彲閫夛級
magic.minio.upload.max-size=50MB                # 鍗曟枃浠朵笂浼犳渶澶уぇ灏?
```

## 浣跨敤鏂瑰紡

### 鏂瑰紡涓€锛氶€氳繃鑿滃崟璁块棶

#### 鑷姩鍚堝苟鑿滃崟锛坈omposite-menu=true锛?

褰撳涓婚」鐩?`application.properties` 璁剧疆 `jmix.ui.composite-menu=true` 鏃讹紝鎻掍欢鑿滃崟浼氳嚜鍔ㄥ悎骞跺埌瀹夸富鑿滃崟涓€?

#### 鎵嬪姩娣诲姞鑿滃崟锛坈omposite-menu=false锛屾帹鑽愶級

褰?`jmix.ui.composite-menu=false` 鏃讹紝闇€瑕佹墜鍔ㄥ湪瀹夸富椤圭洰鐨?`menu.xml` 涓坊鍔犺彍鍗曢」锛?

```xml
<menu id="data-management" title="鏁版嵁绠＄悊" opened="true">
    <item view="minio_BrowserView" title="MinIO 绠＄悊"/>
</menu>
```

> **鎻愮ず**锛氭帹鑽愪娇鐢?`composite-menu=false` + 鎵嬪姩娣诲姞鑿滃崟锛岃繖鏍峰彲浠ョ簿纭帶鍒惰彍鍗曚綅缃拰灞傜骇锛岄伩鍏嶆棤鍏崇殑鎻掍欢鑿滃崟娣峰叆銆?

### 鏂瑰紡浜岋細鍦ㄨ鍥句腑寮曠敤

濡傛灉闇€瑕佸湪鑷畾涔夎鍥句腑闆嗘垚 MinIO 鍔熻兘锛屽彲浠ョ户鎵挎垨缁勫悎 MinioBrowserView锛?

```java
@Route(value = "my-minio", layout = DefaultMainViewParent.class)
@ViewController(id = "my_MinioView")
@ViewDescriptor(path = "minio/minio-browser-view.xml")
public class MyMinioView extends MinioBrowserView {
    // 鑷畾涔夋墿灞曢€昏緫
}
```

> 娉ㄦ剰锛歚layout` 搴斾娇鐢?`DefaultMainViewParent.class` 浠ヤ繚鎸佸簲鐢ㄧ殑涓荤晫闈㈠竷灞€銆?

### 鏂瑰紡涓夛細鐩存帴娉ㄥ叆鏈嶅姟

```java
@Autowired
private MinioService minioService;

// Bucket 鎿嶄綔
minioService.listBuckets();
minioService.createBucket(name);
minioService.deleteBucket(name);
minioService.renameBucket(oldName, newName);

// 鏂囦欢鎿嶄綔
minioService.listObjects(bucket, prefix);
minioService.uploadFile(bucket, objectName, inputStream, size);
minioService.downloadFile(bucket, objectName);
minioService.deleteFile(bucket, objectName);
minioService.createFolder(bucket, folderPath);
minioService.batchDelete(bucket, items);

// 杈呭姪鏂规硶
minioService.countFiles(bucket, folderPath);
minioService.listFolderObjectPaths(bucket, folderPath);
minioService.formatSize(bytes);
```

## 瀹夊叏閰嶇疆

鎻掍欢涓嶉瀹氫箟瑙掕壊銆傝鍦ㄥ涓婚」鐩腑鏍规嵁闇€瑕佽嚜瀹氫箟瑙掕壊鍜屾潈闄愶紝閫氳繃 `@ViewPolicy` 鎺у埗瑙嗗浘璁块棶锛?

```java
@ResourceRole(name = "MinIO User", code = "minio-user")
public interface MinioUserRole {

    @ViewPolicy(viewIds = "minio_BrowserView")
    void minioBrowserView();
}
```

## i18n

鎻掍欢鍐呯疆涓枃锛堥粯璁わ級鍜岃嫳鏂囧弻璇敮鎸侊紝浼氭牴鎹敤鎴?Locale 鑷姩鍒囨崲銆?

## 鎶€鏈爤

- Jmix 2.8.1
- Vaadin Flow
- MinIO Java SDK 8.5.10
- 闆跺閮ㄤ緷璧栵紙涓嶅惈 Lombok锛?

## 鏋勫缓

```bash
./gradlew build
```

## 鍙戝竷鍒版湰鍦?Maven

```bash
./gradlew publishToMavenLocal
```

## License

Apache License 2.0
