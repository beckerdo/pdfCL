package info.danbecker.pdfcl;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfResources;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.ReaderProperties;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.AreaBreakType;
import com.itextpdf.layout.property.TextAlignment;

/**
 * A command line tool for editing PDF (Postscript Document Format) files
 * 
 * This tool allows you to create, append, split/merge, delete pages in a PDF file.
 * 
 * @author <a href="mailto://dan@danbecker.info">Dan Becker</a>
 */
public class PdfCL {
    /** LOGGER */
    public static final Logger LOGGER = LoggerFactory.getLogger(PdfCL.class);

    public static final String[] SRC = { "resources/input.pdf" };
    public static final String DEST = "resources/output.pdf";
    public static final String CMD_DELIM = "\\s*,\\s*"; // 0* whitespace, comma, 0* whitespace

    protected static String verb;
    protected static String[] srcs;
    protected static String dest;
    protected static String number;
    protected static List<Integer> list;
    
    public static Map<Byte,String> nameMap = new HashMap<>();


    public static void initMap( Map<Byte,String> nameMap ) {
       nameMap.put( PdfName.Image.getType(), "image" );
       nameMap.put( PdfName.Obj.getType(), "obj" );
       nameMap.put( PdfName.Stream.getType(), "stream" );
    }
    
    // Constructors
    // Runtime
    public static void main(String[] args) throws Exception {
        // Parse command line options
        parseOptions(args);
        initMap( nameMap );

        if (null != verb && verb.length() > 0) {
            switch (verb) {
            case "create": {
                new PdfCL().createPdf(dest, Integer.parseInt(number));
                break;
            }
            case "concatenate": {
                new PdfCL().concatenatePdf(srcs, dest );
                break;
            }
            case "append": {
                new PdfCL().appendPdf(srcs, dest, list);
                break;
            }
            case "splitImages": {
                new PdfCL().splitImages(srcs, dest, list);
                break;
            }
            case "joinImages": {
                new PdfCL().joinImages(srcs, dest);
                break;
            }
            case "autoCrop": {
                new PdfCL().autoCrop(srcs, dest, number);
                break;
            }
            default: {
                LOGGER.info("verb \"" + verb + "\" is unknown");
            }
            }
        }
        LOGGER.info("exiting");
    }

    /** Command line options for this application. */
    public static void parseOptions(String[] args) throws ParseException, IOException {
        // Parse the command line arguments
        final Options options = new Options();
        // Use dash with shortcut (-h) or -- with name (--help).
        options.addOption("h", "help", false, "print the command line options");
        options.addOption("v", "verb", true, "action to perform");
        options.addOption("n", "number", true, "number, such as number of pages");
        options.addOption("l", "list", true, "list of comma-separated, such as pages, names, etc.");
        options.addOption("s", "src", true, "list of comma-separated input PDF files");
        options.addOption("d", "dest", true, "output PDF file");

        final CommandLineParser cliParser = new DefaultParser();
        final CommandLine line = cliParser.parse(options, args);
        // line.getArgList(); // Retrieve any left-over non-recognized options and
        // arguments

        LOGGER.info("hasHelp=" + line.hasOption("help"));
        // Gather command line arguments for execution
        if (line.hasOption("help")) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java -jar pdfcl.jar <options> info.danbecker.pdfcl.PdfCL", options);
            System.exit(0);
        }
        if (line.hasOption("verb")) {
            verb = line.getOptionValue("verb");
            LOGGER.info("verb=" + verb);
        }
        if (line.hasOption("src")) {
            String option = line.getOptionValue("src");
            srcs = option.split(CMD_DELIM);
            LOGGER.info("srcs=" + Arrays.toString( srcs ));
        } else {
            srcs = SRC;
        }
        if (line.hasOption("dest")) {
            dest = line.getOptionValue("dest");
        } else {
            dest = DEST;
        }
        LOGGER.info("dest=" + dest);
        if (line.hasOption("number")) {
            number = line.getOptionValue("number");
            LOGGER.info("number=" + number);
        }
        if (line.hasOption("list")) {
            String stringList = line.getOptionValue("list");
            List<String> items = Arrays.asList(stringList.split(CMD_DELIM));
            list = new LinkedList<Integer>();
            for (String item : items) {
                list.add(Integer.parseInt(item));
            }
            LOGGER.info("list=" + list);
        } else {
            list = Arrays.asList();
        }
    }

    /** Make/create directory structure for given file name or path. */
    public static boolean mkdirs(String dest) {
        return new File(dest).getParentFile().mkdirs();
    }

    /** States if file exists, is readable, and is non-zero length. */
    public static boolean fileReadable(String dest) {
        File src = new File(dest);
        if (src.exists() && src.canRead() && src.length() > 0) {
            return true;
        }
        return false;
    }

    /**
     * Creates a dummy PDF with a number of text pages.
     * 
     * @param dest
     *            output file name
     * @param numPages
     *            number of pages
     * @throws IOException
     */
    public void createPdf(String dest, int numPages) throws IOException {
        mkdirs(dest);

        // Initialize PDF writer
        PdfWriter writer = new PdfWriter(dest);

        // Initialize PDF document
        PdfDocument pdf = new PdfDocument(writer);

        // Initialize document
        Document document = new Document(pdf);

        // Add pages to the document
        AreaBreak areaBreak = new AreaBreak(AreaBreakType.NEXT_PAGE);
        if (numPages == 0) {
            document.add(new Paragraph("")); // documents cannot be blank when closed
        } else {
            for (int i = 0; i < numPages; i++) {
                document.add(new Paragraph("Page" + Integer.toString(i + 1)));
                if (i + 1 < numPages) {
                    document.add(areaBreak);
                }
            }
        }

        // Close document
        document.close();
        LOGGER.info("\"" + dest + "\" created");
    }

    /** 
     * Appends a list of pages from a list of input files to an output file.
     * Contents in the output file are preserved.
     * @param srcs
     * @param dest
     * @param pagesToMerge
     * @throws IOException
     */
    public void appendPdf(String[] srcs, String dest, List<Integer> pagesToMerge) throws IOException {
        // Check and optionally copy or create destination file
        PdfDocument resultDoc = null;
        mkdirs(dest);
        File destFile = new File(dest);
        if (destFile.exists() && destFile.length() > 0) {
            LOGGER.info("File \"" + destFile + "\" exists=" + destFile.exists() + ", canRead=" + destFile.canRead() + ", length="
                    + destFile.length());
            byte[] byteArray = Files.readAllBytes(destFile.toPath());
            PdfDocument originalDoc = new PdfDocument(
               new PdfReader(new RandomAccessSourceFactory().createSource(byteArray), new ReaderProperties()));
            resultDoc = new PdfDocument(new PdfWriter(dest));
            LOGGER.info("Original numPages=" + originalDoc.getNumberOfPages());
            originalDoc.copyPagesTo( 1, originalDoc.getNumberOfPages(), resultDoc );
            originalDoc.close(); 
        } else {
            resultDoc = new PdfDocument(new PdfWriter(dest));
        }
        // resultDoc.initializeOutlines();

        // Copy 
        for (String src : srcs) {
            LOGGER.info("Source file=" + src);
            PdfDocument srcDoc = new PdfDocument(new PdfReader(src));
            int numPages = srcDoc.getNumberOfPages();
            LOGGER.info("NumPages=" + numPages);
            LOGGER.info("Pages=" + pagesToMerge);
            if ( null != pagesToMerge ) {
               srcDoc.copyPagesTo(pagesToMerge, resultDoc);
            } else {
               srcDoc.copyPagesTo(1, srcDoc.getNumberOfPages(), resultDoc);                
            }
            srcDoc.close();
        } // srcs

        resultDoc.close();        
    }

    /** Reverse all pages in the given source files. */
    protected void reversePdf(String[] srcs) throws Exception {
        for (String src : srcs) {
            LOGGER.info("Source file=" + src);
            File srcFile = new File(src);
            if (fileReadable(dest)) {
                LOGGER.info("File \"" + srcFile + "\" exists=" + srcFile.exists() + ", canRead=" + srcFile.canRead() + ", length="
                        + srcFile.length());
                return;
            }
            byte[] byteArray = Files.readAllBytes(srcFile.toPath());
            PdfDocument srcDoc = new PdfDocument(
                    new PdfReader(new RandomAccessSourceFactory().createSource(byteArray), new ReaderProperties()));
            PdfDocument resultDoc = new PdfDocument(new PdfWriter(src));
            resultDoc.initializeOutlines();

            List<Integer> pages = new ArrayList<>();
            int numPages = srcDoc.getNumberOfPages();
            LOGGER.info("NumPages=" + numPages);
            for (int pagei = numPages; pagei > 0; pagei--) {
                pages.add(pagei);
            }
            LOGGER.info("Pages=" + pages);
            srcDoc.copyPagesTo(pages, resultDoc);

            srcDoc.close();
            resultDoc.close();
        } // srcs
    }

    /** Copies all input file pages to a given output file page. */
    public void concatenatePdf(String[] srcs, String dest) throws IOException {
        PdfDocument pdfDest = new PdfDocument(new PdfWriter(dest));

        PdfMerger merger = new PdfMerger(pdfDest);
        for (String src : srcs) {
            LOGGER.info("Source file=" + src);
            PdfDocument pdfSrc = new PdfDocument(new PdfReader(src));
            merger.merge(pdfSrc, 1, pdfSrc.getNumberOfPages());
            pdfSrc.close();
        }

        pdfDest.close();
    }
    
    /** 
     * Splits images in a given set of files/pages to output path.
     * @param srcs
     * @param dest
     * @param pagesToMerge
     * @throws IOException
     */
    public void splitImages(String[] srcs, String dest, List<Integer> pagesToMerge) throws IOException {
        // Treat dest as a path and make dirs
        new File(dest).mkdirs();

        // Copy 
        for (String src : srcs) {
            PdfDocument srcDoc = new PdfDocument(new PdfReader(src));
            LOGGER.info("Source file=" + src + ", numPages=" + srcDoc.getNumberOfPages() + ", numObjects=" + srcDoc.getNumberOfPdfObjects());
           
           for (int i = 1; i <= srcDoc.getNumberOfPdfObjects(); i++) {
               PdfObject obj = srcDoc.getPdfObject(i);
               if (obj != null && obj.isStream()) {                   
                   PdfStream stream = (PdfStream) obj;
                   // byte[] b = stream.getBytes();
                   
                   PdfName pdfName = stream.getAsName(PdfName.Subtype);
                   if (PdfName.Image.equals( pdfName )) {                       
                       PdfImageXObject image = new PdfImageXObject(stream);
                       BufferedImage bi = image.getBufferedImage();                       
                       LOGGER.info("Object " + i + ", subtype=image, size=" + bi.getWidth() + "x" + bi.getHeight() + ",type=" + bi.getType());
                       
                       if ( !dest.endsWith( File.separator )) {
                           dest += File.separator;
                       }                       
                       File outputfile = new File(dest + "obj-" + i +".jpg"); 
                       ImageIO.write(bi, "jpg", outputfile);


                   } else {
                       if ( null == pdfName ) {
                           // LOGGER.info("Object " + i + ", subtype=null");
                       } else {
                           LOGGER.info("Object " + i + ", subtype=" + pdfName.getValue());                       
                       }
                   }
             } // if Stream
           }
          
           // Access via page number
           for (int i = 1; i <= srcDoc.getNumberOfPages(); i++) {
              PdfPage page = srcDoc.getPage(i);
              PdfResources resources = page.getResources();
                                
              Set<PdfName> names = resources.getResourceNames();
              for ( PdfName name : names) {
                  LOGGER.info( "Page " + i + ", resource name=" + name.toString() + ", type="  + name.getType() + ", typeName=" + getNameString( name ));                    
                  if ( PdfName.Stream.getType() == name.getType() ) {
                      // LOGGER.info( "stream subtype=" + resources.get() );
                      PdfStream stream = (PdfStream) resources.getResourceObject(PdfName.Stream, name);                      
                      // PdfName subType = stream.getAsName(PdfName.Subtype);
                      // LOGGER.info( "Page " + i + ", resource name=" + name.toString() + ", subtype=" + subType );                    
                  }
              }
           }
           
           srcDoc.close();
        } // srcs
    }
    
    public static String getNameString( PdfName pdfName ) {
        String name = nameMap.get( pdfName.getType() );
        
        if ( null == name || name.length() < 1 ) {
            return "unknown";
        }
        return name;        
    }
    
    public Image getWatermarkedImage(PdfDocument pdfDoc, Image img, String watermark) {
        float width = img.getImageScaledWidth();
        float height = img.getImageScaledHeight();
        PdfFormXObject template = new PdfFormXObject(new Rectangle(width, height));
        Canvas canvas = new Canvas(template, pdfDoc);        
        canvas.add(img).
                setFontColor(DeviceGray.WHITE).
                showTextAligned(watermark, width / 2, height / 2, TextAlignment.CENTER, (float) Math.PI / 6);
        canvas.close();
        return new Image(template);
    }
 
//    public static final String IMAGE1 = "resources/img/bruno.jpg";
//    public static final String IMAGE2 = "resources/img/dog.bmp";
//    public static final String IMAGE3 = "resources/img/fox.bmp";
//    public static final String IMAGE4 = "resources/img/bruno_ingeborg.jpg";
    
    protected void joinImages(String[] srcs, String dest ) throws Exception {
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(dest));
        Document doc = new Document(pdfDoc);
        Image image = new Image(ImageDataFactory.create(srcs[0])).scaleToFit(200, 350);
        doc.add(getWatermarkedImage(pdfDoc, image, "Bruno"));
        doc.add(getWatermarkedImage(pdfDoc, new Image(ImageDataFactory.create(srcs[1])), "Dog"));
        doc.add(getWatermarkedImage(pdfDoc, new Image(ImageDataFactory.create(srcs[2])), "Fox"));
        image = new Image(ImageDataFactory.create(srcs[3])).scaleToFit(200, 350);
        doc.add(getWatermarkedImage(pdfDoc, image, "Bruno and Ingeborg"));
        doc.close();
    }
    
    /** 
     * Takes source files or directory of source files, breaks them into individuals, passes them on.
     * @param srcs
     * @param dest
     * @throws IOException
     */
    public void autoCrop(String[] srcs, String dest, String number ) throws IOException {
        // Treat dest as a path and make dirs
        File destFile = new File(dest);
        destFile.mkdirs();
        
        Float tolerance = Float.parseFloat(number);

        // Copy 
        for (String src : srcs) {
            File srcFile = new File ( src );
            if ( srcFile.canRead() ) {
                if ( srcFile.isDirectory() ) {
                    ArrayList<File> srcFiles = new ArrayList<File>(Arrays.asList(srcFile.listFiles()));
                    for ( File oneFile : srcFiles ) {
                        autoCrop( oneFile, destFile, tolerance );                        
                    }
                } else {
                    autoCrop( srcFile, destFile, tolerance );
                }                
            } else {
                LOGGER.info("File \"" + srcFile + "\" exists=" + srcFile.exists() + ", canRead=" + srcFile.canRead() + ", length="
                        + srcFile.length());
            }
        } // srcs
    }

    /** 
     * Takes one source files, auto crops, and places in dest file.
     * @param srcs
     * @param dest
     * @throws IOException
     */
    public void autoCrop(File srcFile, File destFile, Float tolerance ) throws IOException {
        if (srcFile.exists() && srcFile.isFile() && srcFile.canRead()) {
            BufferedImage in = ImageIO.read(srcFile);
            LOGGER.info("Input image size=" + in.getWidth() + "x" + in.getHeight() + ", type=" + in.getType());

            Path outputPath = null;
            if ( destFile.isDirectory() ) {
                outputPath = Paths.get(destFile.toString(), srcFile.getName());
            } else {
                outputPath = Paths.get(destFile.toString(), srcFile.getName());
            }
                           
            BufferedImage out = getCroppedImage( in, tolerance );
            LOGGER.info("Output image size=" + out.getWidth() + "x" + out.getHeight() + ", type=" + out.getType());

            ImageIO.write(out, "jpg", outputPath.toFile());
        } else {
            LOGGER.info("File \"" + srcFile + "\" exists=" + srcFile.exists() + ", canRead=" + srcFile.canRead()
                    + ", length=" + srcFile.length());
        }            
    }

    /**
     * Crop all 4 sides of an images, removing border color pixels. 
     * Originally from <a href="https://stackoverflow.com/questions/10678015/how-to-auto-crop-an-image-white-border-in-java">Stackoverflow</a>.
     * Modified to vote on which corner pixel to use as the base color.
     * @param source
     * @param tolerance
     * @return
     */
    public static BufferedImage getCroppedImage(BufferedImage source, double tolerance) {
        // Get our top-left pixel color as our "baseline" for cropping
        // TODO Get all four corners and the baseColor is the most common corner color.
        int baseColor = source.getRGB(0, 0);

        int width = source.getWidth();
        int height = source.getHeight();

        int topY = Integer.MAX_VALUE, topX = Integer.MAX_VALUE;
        int bottomY = -1, bottomX = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (colorWithinTolerance(baseColor, source.getRGB(x, y), tolerance)) {
                    if (x < topX)
                        topX = x;
                    if (y < topY)
                        topY = y;
                    if (x > bottomX)
                        bottomX = x;
                    if (y > bottomY)
                        bottomY = y;
                }
            }
        }

        BufferedImage destination = new BufferedImage((bottomX - topX + 1), (bottomY - topY + 1),
                BufferedImage.TYPE_INT_ARGB);

        destination.getGraphics().drawImage(source, 0, 0, destination.getWidth(), destination.getHeight(),
             topX, topY, bottomX+1, bottomY+1, null);

        return destination;
    }

    /**
     * Return true or false if a given int-encoded ARGB pixel is within a certain percentage distance of another.
     * @param a
     * @param b
     * @param tolerance
     * @return
     */
    private static boolean colorWithinTolerance(int a, int b, double tolerance) {
        int aAlpha = (int) ((a & 0xFF000000) >>> 24); // Alpha level
        int aRed = (int) ((a & 0x00FF0000) >>> 16); // Red level
        int aGreen = (int) ((a & 0x0000FF00) >>> 8); // Green level
        int aBlue = (int) (a & 0x000000FF); // Blue level

        int bAlpha = (int) ((b & 0xFF000000) >>> 24); // Alpha level
        int bRed = (int) ((b & 0x00FF0000) >>> 16); // Red level
        int bGreen = (int) ((b & 0x0000FF00) >>> 8); // Green level
        int bBlue = (int) (b & 0x000000FF); // Blue level

        double distance = Math.sqrt((aAlpha - bAlpha) * (aAlpha - bAlpha) + (aRed - bRed) * (aRed - bRed)
                + (aGreen - bGreen) * (aGreen - bGreen) + (aBlue - bBlue) * (aBlue - bBlue));

        // 510.0 is the maximum distance between two colors
        // (0,0,0,0 -> 255,255,255,255)
        double percentAway = distance / 510.0d;

        return (percentAway > tolerance);
    }
}