package kr.boothrock.api.map.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import kr.boothrock.api.common.repository.SqlStore;
import kr.boothrock.api.common.service.ApiException;
import kr.boothrock.api.common.service.EventAccess;
import kr.boothrock.api.common.service.Rules;
import kr.boothrock.api.map.repository.MediaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000L;
    private static final int MAX_DIMENSION = 16_000;
    private static final byte[] PNG_MAGIC = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final Pattern LOCAL_KEY = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)");
    private final MediaRepository media;
    private final EventAccess access;
    private final Path directory;

    public MediaService(MediaRepository media, EventAccess access,
            @Value("${boothrock.media.directory:.local/media}") String directory) {
        this.media = media;
        this.access = access;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Transactional
    public Map<String, Object> upload(UUID eventId, UUID accountId, MultipartFile file) {
        access.lockEvent(eventId);
        access.requireManager(eventId, accountId);
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        Rules.input(!file.isEmpty(), "An image file is required.");
        byte[] bytes;
        try (InputStream input = file.getInputStream()) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException exception) {
            throw new ApiException(400, "VALIDATION_ERROR", "Unable to read the uploaded image.");
        }
        if (bytes.length > MAX_BYTES) throw tooLarge();
        ImageInfo info = validateImage(bytes, file.getContentType());
        UUID id = UUID.randomUUID();
        String key = id + (info.mimeType().equals("image/png") ? ".png" : ".jpg");
        writeFile(key, bytes);
        media.insert(id, eventId, accountId, key, info.mimeType(), bytes.length, info.width(), info.height());
        return description(media.asset(eventId, id));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> requireAsset(UUID eventId, UUID assetId) {
        Map<String, Object> asset = media.asset(eventId, assetId);
        checkedPath(asset);
        return asset;
    }

    public Map<String, Object> description(Map<String, Object> asset) {
        return Rules.result("mediaAssetId", asset.get("id"), "widthPx", asset.get("widthPx"),
                "heightPx", asset.get("heightPx"), "readUrl", readUrl(SqlStore.uuid(asset, "id")),
                "expiresAt", null);
    }

    public static String readUrl(UUID assetId) {
        return "/api/media-assets/" + assetId + "/content";
    }

    @Transactional(readOnly = true)
    public MediaContent content(UUID assetId, UUID accountId) {
        Map<String, Object> asset = media.asset(assetId);
        UUID eventId = SqlStore.uuid(asset, "eventId");
        boolean manager = accountId != null && access.isManager(eventId, accountId);
        boolean internalNotice = accountId != null && media.hasInternalAnnouncementReference(assetId, eventId,
                access.isStaff(eventId, accountId), access.isOperator(eventId, accountId));
        if (!manager && !internalNotice && !media.hasPublicReference(assetId)) throw ApiException.notFound();
        Path path = checkedPath(asset);
        long expected = ((Number) asset.get("sizeBytes")).longValue();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw ApiException.notFound();
        try (var channel = Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                InputStream input = Channels.newInputStream(channel)) {
            if (channel.size() != expected) throw ApiException.notFound();
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length != expected) throw ApiException.notFound();
            return new MediaContent((String) asset.get("mimeType"), bytes);
        } catch (IOException exception) {
            throw ApiException.notFound();
        }
    }

    private Path checkedPath(Map<String, Object> asset) {
        String key = (String) asset.get("objectKey");
        String mime = (String) asset.get("mimeType");
        long size = ((Number) asset.get("sizeBytes")).longValue();
        int width = ((Number) asset.get("widthPx")).intValue();
        int height = ((Number) asset.get("heightPx")).intValue();
        if (!"local".equals(asset.get("bucket")) || key == null || !LOCAL_KEY.matcher(key).matches()
                || !("image/png".equals(mime) && key.endsWith(".png")
                    || "image/jpeg".equals(mime) && key.endsWith(".jpg"))
                || size <= 0 || size > MAX_BYTES || !validDimensions(width, height)) {
            throw ApiException.notFound();
        }
        return directory.resolve(key);
    }

    private static ImageInfo validateImage(byte[] bytes, String declaredMime) {
        boolean png = bytes.length >= PNG_MAGIC.length
                && Arrays.equals(PNG_MAGIC, Arrays.copyOf(bytes, PNG_MAGIC.length));
        boolean jpeg = bytes.length >= 3 && bytes[0] == (byte) 0xff
                && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
        String mime = png ? "image/png" : jpeg ? "image/jpeg" : null;
        if (mime == null || !mime.equalsIgnoreCase(declaredMime)) throw unsupported();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw unsupported();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                Rules.input(validDimensions(width, height),
                        "Image dimensions must be positive, at most 16000 per side and at most 16000000 pixels.");
                // Check dimensions before allocating a decoded raster to bound decompression memory.
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                    throw unsupported();
                }
                decoded.flush();
                return new ImageInfo(mime, width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw unsupported();
        }
    }

    private static boolean validDimensions(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_DIMENSION && height <= MAX_DIMENSION
                && (long) width * height <= MAX_PIXELS;
    }

    private void writeFile(String key, byte[] bytes) {
        Path path = directory.resolve(key);
        boolean created = false;
        try {
            Files.createDirectories(directory);
            try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) deleteFile(path);
                    }
                });
                output.write(bytes);
            }
        } catch (IOException exception) {
            if (created) deleteFile(path);
            throw new ApiException(500, "MEDIA_STORAGE_ERROR", "Unable to store the image.");
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Unable to remove rolled-back media file {}", path.getFileName(), exception);
        }
    }

    private static ApiException unsupported() {
        return new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", "Upload a valid PNG or JPEG with its matching MIME type.");
    }

    private static ApiException tooLarge() {
        return new ApiException(413, "FILE_TOO_LARGE", "Images must not exceed 10 MiB.");
    }

    private record ImageInfo(String mimeType, int width, int height) {}

    public record MediaContent(String mimeType, byte[] bytes) {}
}
