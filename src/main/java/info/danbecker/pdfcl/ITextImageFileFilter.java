package info.danbecker.pdfcl;

import java.io.File;
import java.io.FileFilter;

/**
 * A filter which accepts files ending IText readable image extensions.
 * 
 * @author <a href="mailto://dan@danbecker.info">Dan Becker</a>
 */
public class ITextImageFileFilter implements FileFilter {
    /** Ideally would like to build this list from com.itextpdf.io.image.ImageType */
    private final String[] okFileExtensions = new String[] 
      { "bmp", "gif", "jbig2", "jpeg", "jpg", "png", "ps", "raw", "tiff", "tif", "wmf" };
    
    public boolean accept(File file) {
        for (String extension : okFileExtensions) {
            if (file.getName().toLowerCase().endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}