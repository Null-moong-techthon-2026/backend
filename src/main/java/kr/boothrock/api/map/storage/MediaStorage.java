package kr.boothrock.api.map.storage;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.regex.Pattern;
import kr.boothrock.api.common.service.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

/** Stores validated images. Reads use the persisted bucket, not the current write provider. */
@Component
public class MediaStorage {
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|pdf)");
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final Path directory;
    private final String provider;
    private final String bucket;
    private final S3Client s3;

    @Autowired
    public MediaStorage(@Value("${boothrock.media.directory:.local/media}") String directory,
            @Value("${boothrock.media.provider:local}") String provider,
            @Value("${boothrock.media.s3.bucket:}") String bucket,
            @Value("${boothrock.media.s3.region:}") String region) {
        this(Path.of(directory), provider, bucket, client(provider, bucket, region));
    }

    MediaStorage(Path directory, String provider, String bucket, S3Client s3) {
        if (!provider.equals("local") && !provider.equals("s3")) {
            throw new IllegalArgumentException("Media provider must be local or s3");
        }
        if (provider.equals("s3") && (bucket.isBlank() || s3 == null)) {
            throw new IllegalArgumentException("S3 media requires a bucket and region");
        }
        if (bucket.equals("local")) throw new IllegalArgumentException("S3 bucket cannot use the reserved name local");
        this.directory = directory.toAbsolutePath().normalize();
        this.provider = provider;
        this.bucket = bucket;
        this.s3 = s3;
    }

    private static S3Client client(String provider, String bucket, String region) {
        if (!provider.equals("local") && !provider.equals("s3")) throw new IllegalArgumentException("Invalid media provider");
        if (bucket.isBlank()) {
            if (provider.equals("s3")) throw new IllegalArgumentException("S3 media bucket is required");
            return null;
        }
        if (region.isBlank() || bucket.equals("local")) throw new IllegalArgumentException("Invalid S3 media configuration");
        return S3Client.builder().region(Region.of(region))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .build(); // Default credential chain: EC2 instance role in staging; no embedded keys.
    }

    public String writeBucket() { return provider.equals("s3") ? bucket : "local"; }

    public void validate(String storedBucket, String key) {
        if (key == null || !KEY.matcher(key).matches()
                || !("local".equals(storedBucket) || s3 != null && bucket.equals(storedBucket))) {
            throw ApiException.notFound();
        }
    }

    public void put(String key, String mime, byte[] bytes) {
        validate(writeBucket(), key);
        if (provider.equals("s3")) {
            try {
                s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(mime).ifNoneMatch("*").build(), RequestBody.fromBytes(bytes));
                return;
            } catch (software.amazon.awssdk.core.exception.SdkException exception) {
                throw storageError();
            }
        }
        Path path = directory.resolve(key);
        boolean created = false;
        try {
            Files.createDirectories(directory);
            try (var out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                out.write(bytes);
            }
        } catch (IOException exception) {
            if (created) {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { /* Reconcile orphan files separately. */ }
            }
            throw storageError();
        }
    }

    public byte[] read(String storedBucket, String key, long expected) {
        validate(storedBucket, key);
        if (expected <= 0 || expected > MAX_BYTES) throw ApiException.notFound();
        if (!storedBucket.equals("local")) {
            try (var input = s3.getObject(GetObjectRequest.builder().bucket(storedBucket).key(key).build())) {
                if (input.response().contentLength() == null || input.response().contentLength() != expected) {
                    input.abort();
                    throw ApiException.notFound();
                }
                byte[] bytes = input.readNBytes((int) expected + 1);
                if (bytes.length != expected) { input.abort(); throw ApiException.notFound(); }
                return bytes;
            } catch (S3Exception exception) {
                if (exception.statusCode() == 404) throw ApiException.notFound();
                throw storageError();
            } catch (IOException exception) {
                throw storageError();
            } catch (software.amazon.awssdk.core.exception.SdkException exception) {
                throw storageError();
            }
        }
        Path path = directory.resolve(key);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw ApiException.notFound();
        try (var channel = Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                InputStream input = Channels.newInputStream(channel)) {
            if (channel.size() != expected) throw ApiException.notFound();
            byte[] bytes = input.readNBytes((int) expected + 1);
            if (bytes.length != expected) throw ApiException.notFound();
            return bytes;
        } catch (IOException exception) { throw ApiException.notFound(); }
    }

    public void delete(String storedBucket, String key) {
        validate(storedBucket, key);
        if (!storedBucket.equals("local")) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(storedBucket).key(key).build());
        } else {
            try { Files.deleteIfExists(directory.resolve(key)); }
            catch (IOException exception) { throw storageError(); }
        }
    }

    private static ApiException storageError() {
        return new ApiException(503, "MEDIA_STORAGE_ERROR", "File storage is temporarily unavailable.");
    }

    @PreDestroy
    public void close() { if (s3 != null) s3.close(); }
}
