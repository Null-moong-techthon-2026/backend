package kr.boothrock.api.map.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import kr.boothrock.api.common.service.*;
import org.apache.pdfbox.Loader;
import org.springframework.web.multipart.MultipartFile;

/** Shared validation for private PDF/image documents and announcement files. */
public final class UploadedFileValidator {
    private static final int MAX_BYTES=10*1024*1024;
    private UploadedFileValidator() {}
    public static Validated read(MultipartFile file) {
        if(file.getSize()>MAX_BYTES)throw tooLarge();
        Rules.input(!file.isEmpty(),"A file is required");
        byte[] bytes;
        try(var in=file.getInputStream()){bytes=in.readNBytes(MAX_BYTES+1);}
        catch(IOException e){throw new ApiException(400,"VALIDATION_ERROR","Cannot read the file");}
        if(bytes.length>MAX_BYTES)throw tooLarge();
        return new Validated(safeName(file.getOriginalFilename()),validate(bytes,file.getContentType()),bytes);
    }
    public record Validated(String fileName,String mimeType,byte[] bytes) {}
    public static String safeName(String name) {
        if(name==null||name.isBlank())return "document";
        String leaf=name.replace('\\','/');leaf=leaf.substring(leaf.lastIndexOf('/')+1);
        Rules.input(!leaf.isBlank()&&leaf.length()<=255&&leaf.codePoints().noneMatch(c->Character.isISOControl(c)),"Invalid file name (max 255)");
        return leaf;
    }
    public static String validate(byte[] bytes,String declared) {
        if("image/png".equalsIgnoreCase(declared)||"image/jpeg".equalsIgnoreCase(declared)) {
            MediaService.validateUploadedImage(bytes,declared);return declared.toLowerCase(java.util.Locale.ROOT);
        }
        if(!"application/pdf".equalsIgnoreCase(declared)||bytes.length<5
            ||!Arrays.equals(Arrays.copyOf(bytes,5),"%PDF-".getBytes(StandardCharsets.US_ASCII)))throw unsupported();
        try(var pdf=Loader.loadPDF(bytes)) {
            if(pdf.isEncrypted()||pdf.getNumberOfPages()<1)throw unsupported();
        }catch(IOException|IllegalArgumentException e){throw unsupported();}
        return "application/pdf";
    }
    private static ApiException unsupported(){return new ApiException(415,"UNSUPPORTED_MEDIA_TYPE","Upload a valid, unencrypted PDF, PNG or JPEG");}
    private static ApiException tooLarge(){return new ApiException(413,"FILE_TOO_LARGE","Documents must not exceed 10 MiB");}
}
