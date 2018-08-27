package info.danbecker.pdfcl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfBoolean;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfLiteral;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfResources;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.PdfString;
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
    protected static String color;
    
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
                info.danbecker.pdfcl.Image.autoCrop(srcs, dest, color, number);
                break;
            }
            case "pdfTree": {
                new PdfCL().pdfTree(srcs, dest);
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
        options.addOption("n", "number", true, "number, such as number of pages or percentage");
        options.addOption("l", "list", true, "list of comma-separated, such as pages, names, etc.");
        options.addOption("s", "src", true, "list of comma-separated input PDF files");
        options.addOption("d", "dest", true, "output PDF file");
        options.addOption("c", "color", true, "comma separated ARGB used for image processing");

        final CommandLineParser cliParser = new DefaultParser();
        final CommandLine line = cliParser.parse(options, args);
        // line.getArgList(); // Retrieve any left-over non-recognized options and
        // arguments

        // Gather command line arguments for execution
        if (line.hasOption("help")) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java -jar pdfcl.jar <options> info.danbecker.pdfcl.PdfCL", options);
            LOGGER.info("java version=" + Runtime.class.getPackage().getImplementationVersion());
            System.exit(0);
        } else {
            LOGGER.info("help=" + line.hasOption("help"));            
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
        if (line.hasOption("color")) {
            color = line.getOptionValue("color");
            LOGGER.info("color=" + color);
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

            // Access via object number
//            for (int i = 1; i <= srcDoc.getNumberOfPdfObjects(); i++) {
//                PdfObject obj = srcDoc.getPdfObject(i);
//                if (obj != null && obj.isStream()) {                   
//                    PdfStream stream = (PdfStream) obj;
//                    PdfName pdfName = stream.getAsName(PdfName.Subtype);
//                    if ( null != pdfName ) {
//                        LOGGER.info("Page " + i + ", resource name=" + pdfName.toString() + ", typeName=" + getNameString(pdfName));
//                    } else {
//                        LOGGER.info("Page " + i + ", resource name=null, typeName=" + getNameString(pdfName));                        
//                    }
//                    
//                    if (PdfName.Image.equals(pdfName)) {
//                        LOGGER.info("Object " + i + ", resource name=" + pdfName.toString() + ", typeName=" + getNameString(pdfName) + " image");
//                        PdfImageXObject image = new PdfImageXObject(stream);
//                        if ( null != image ) {
//                            outputImage( i, pdfName, image );
//                        }
//                    } // image
//                } // if Stream
//            } // object number

            // Access via page number
            for (int i = 1; i <= srcDoc.getNumberOfPages(); i++) {
               PdfPage page = srcDoc.getPage(i);
               PdfResources resources = page.getResources();

               Set<PdfName> names = resources.getResourceNames();
               // LOGGER.info("Page " + i + ", resource count=" + names.size());
               for (PdfName name : names) {
                   LOGGER.info("Page " + i + ", resource name=" + name.toString() + ", typeName=" + getNameString(name));
                   PdfImageXObject image = resources.getImage(name);
                   if ( null != image ) {
                      outputImage( i, name, image );                     
                   }
               }
            } // pages            
           srcDoc.close();
        } // srcs
    }

    /** Output file from given Image */
    public static void outputImage( int element, PdfName pdfName, PdfImageXObject image ) throws IOException {
        if ( null != image ) {
            LOGGER.info("Page " + element + ", resource name=" + pdfName.toString() + 
                    ", size=" + image.getWidth() +"x" + image.getHeight() +
                    ", type=" + image.identifyImageType() 
                    );   
            File outputfile = new File(dest, "e" + element + "-" + pdfName.getValue() + "." + image.identifyImageType().toString().toLowerCase() );
            BufferedImage bi = image.getBufferedImage();
            // ImageIO.write(bi, "jpg", outputfile); // TIF requires Java 9
            ImageIO.write(bi, image.identifyImageFileExtension().toLowerCase(), outputfile);
            LOGGER.info( "Output " + outputfile.getName());
        } else {
            LOGGER.info( "Element=" + element + ", name=" + pdfName + "image=null");            
        }
        
    }
    
    public static String getNameString( PdfName pdfName ) {
        if ( null == pdfName ) {
            return "null name";
        }
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

    /** Adds a list of files or contents of directories as images to an destination pPDF. */
    protected void joinImages(String[] srcs, String dest ) throws Exception {
        mkdirs(dest);        
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(dest));
        Document doc = new Document(pdfDoc);
        // Document doc = new Document(pdfDoc, PageSize.LETTER);
        Rectangle pageSize = doc.getPageEffectiveArea(pdfDoc.getDefaultPageSize());
        LOGGER.info("page size=" + pageSize.toString()); 
        
        // Copy 
        for (String src : srcs) {
            File fileSrc = new File( src );
            if ( fileSrc.exists() && fileSrc.canRead()) {
                if ( fileSrc.isDirectory() ) {
                    File [] contents = fileSrc.listFiles( new ITextImageFileFilter() );
                    for ( File inputFile : contents) {
                        if ( inputFile.exists() && inputFile.canRead()) {
                            Image image = new Image(ImageDataFactory.create(inputFile.getPath()));
                            float scale = 1.0f;
                            if ( image.getImageWidth() > pageSize.getWidth() || image.getImageHeight() > pageSize.getHeight()) {
                                float xScale = pageSize.getWidth() / image.getImageWidth();
                                float yScale = pageSize.getHeight() / image.getImageHeight();
                                if ( xScale < yScale ) {
                                    scale = xScale;
                                } else {                                    
                                    scale = yScale;
                                }
                                // LOGGER.info("x/y/scale=" + xScale + "/" + yScale + "/" + scale ); 
                                image.scale( scale,  scale );
                            }
                            // float maxWidth = PageSize.A4.getWidth() - pageMargin;
                            LOGGER.info("Joining file \"" + inputFile.getPath() + "\"" + 
                               ", size=" + image.getImageWidth() + "x" + image.getImageHeight() +
                               ", scaled=" + image.getImageScaledWidth() + "x" + image.getImageScaledHeight());
                            doc.add(image);
                        } else {
                            LOGGER.info("File \"" + inputFile.getName() + "\" exists=" + inputFile.exists() + ", canRead=" + inputFile.canRead() );                            
                        }
                    }
                } else {
                    LOGGER.info("Joining file \"" + fileSrc.getPath() + "\""  );
                    Image image = new Image(ImageDataFactory.create(fileSrc.getPath()));
                    // image.setAutoScale(true);
                    doc.add(image);
                }
            } else {
                LOGGER.info("File \"" + fileSrc + "\" exists=" + fileSrc.exists() + ", canRead=" + fileSrc.canRead() );
            }
        } // srcs
        
        doc.close();
        pdfDoc.close();
    }

    /** 
     * Shows the structured tree of the PDF document
     * @param srcs
     * @param dest
     * @param pagesToMerge
     * @throws IOException
     */
    public void pdfTree(String[] srcs, String dest) throws IOException {
        // Treat dest as a path and make dirs
        new File(dest).mkdirs();

        // Copy 
        for (String src : srcs) {
            PdfDocument srcDoc = new PdfDocument(new PdfReader(src));
            LOGGER.info("Source file=" + src + ", numPages=" + srcDoc.getNumberOfPages() + ", numObjects=" + srcDoc.getNumberOfPdfObjects());

            PdfDictionary catalog = srcDoc.getCatalog().getPdfObject();
            // PdfDictionary structTreeRoot = catalog.getAsDictionary(PdfName.StructTreeRoot);            
            // visit(structTreeRoot,0);
            visit(new HashSet<PdfObject>(), 0, PdfName.Catalog, catalog, 0);
            
            srcDoc.close();
        } // srcs
    }

    /** Visit each object in the PdfObject tree, by recursing through dictionaries and arrays. */
    public static void visit( Set<PdfObject> visited, int element, PdfName pdfName, PdfObject pdfObject, int level ) {
        if ( null == pdfObject) {
            return;
        }
        
        StringBuffer prefix = new StringBuffer();
        // indent
        for ( int leveli = 0; leveli < level; leveli++ ) {
            prefix.append("   ");
        }
        if ( PdfName.CropBox.equals(pdfName) || PdfName.MediaBox.equals(pdfName)) {
            // Shorten these special arrays
            PdfArray pdfBox = (PdfArray) pdfObject; 
            LOGGER.info( prefix.toString() + "pdfName=" + pdfName.getValue() + ", values=" 
               + ((PdfNumber)pdfBox.get(0)).getValue() + "," + ((PdfNumber)pdfBox.get(1)).getValue() + ","
               + ((PdfNumber)pdfBox.get(2)).getValue() + "," + ((PdfNumber)pdfBox.get(3)).getValue() );
        } else if ( pdfObject.isStream() ) {
            PdfStream pdfStream = (PdfStream) pdfObject;
            PdfName subtype = pdfStream.getAsName(PdfName.Subtype);
            if (PdfName.Image.equals(subtype)) {
                PdfImageXObject image = new PdfImageXObject(pdfStream);
                LOGGER.info( prefix.toString() + "pdfName=" + pdfName.getValue() + ", object" + pdfObjectString( pdfObject) + " (" + image.getWidth() + "x" + image.getHeight() + "), type=" + image.identifyImageType().toString());
            } else if (PdfName.Form.equals(subtype)) {
                PdfFormXObject form = new PdfFormXObject(pdfStream);
                LOGGER.info( prefix.toString() + "pdfName=" + pdfName.getValue() + ", object" + pdfObjectString( pdfObject)  + " (" + form.getWidth() + "x" + form.getHeight() + ")");                
            } else if (PdfName.XML.equals(subtype)) {
                String xmlString = pdfStreamXMLtoString( pdfStream );
                LOGGER.info(prefix.toString() + "pdfName=" + pdfName.getValue() + ", object"
                        + pdfObjectString(pdfObject) + " (" + xmlString + ")");
            } else {
                LOGGER.info(prefix.toString() + "pdfName=" + pdfName.getValue() + ", object" + pdfObjectString( pdfObject)  );                                
            }
        } else if ( pdfObject.isDictionary() ) {
            PdfDictionary pdfDictionary = (PdfDictionary) pdfObject;
            LOGGER.info(  prefix.toString() + "pdfName=" + pdfName.getValue() + ", object" + pdfObjectString( pdfObject) + ", size=" + pdfDictionary.size());
            Set<Map.Entry<PdfName,PdfObject>> entries = pdfDictionary.entrySet();
            int i = 0;
            for ( Map.Entry<PdfName,PdfObject> entry: entries) {
                PdfName key = (PdfName) entry.getKey();
                PdfObject value = (PdfObject) entry.getValue();
                if ( !visited.contains( value )) {
                    visited.add(value);
                    visit(visited, ++i, key, value, level + 1);
                }
            }
        } else if ( pdfObject.isArray() ) {
            PdfArray pdfArray = ((PdfArray)pdfObject);
            LOGGER.info(  prefix.toString() + "pdfName=" + pdfName.getValue()  + ", object=" + pdfObjectString( pdfObject) + ", size=" + pdfArray.size());                
            for ( int i = 0; i < pdfArray.size(); i++  ) {
                PdfObject value = pdfArray.get(i);
                if ( !visited.contains( value )) {
                    visited.add(value);                
                    visit( visited, i, PdfName.Obj, pdfArray.get(i), level + 1);
                }
            }
        } else if ( pdfObject.isString() ) {
            LOGGER.info(  prefix.toString() + "pdfName=" + pdfName.getValue()  + ", object=" + pdfObjectString( pdfObject) + ", string=" + ((PdfString)pdfObject).getValue());                
        } else if ( pdfObject.isName() ) {
            LOGGER.info(  prefix.toString() + "pdfName=" + pdfName.toString()  + ", object=" + pdfObjectString( pdfObject) );                
        } else if ( pdfObject.isNumber() ) {
            LOGGER.info(  prefix.toString() + "pdfName=" + pdfName.getValue()  + ", object=" + pdfObjectString( pdfObject) + ", value=" + ((PdfNumber)pdfObject).getValue());                
        } else if ( pdfObject.isBoolean() ) {
            LOGGER.info(  prefix.toString() + "pdfName=" + pdfName.getValue()  + ", object=" + pdfObjectString( pdfObject) + ", value=" + ((PdfBoolean)pdfObject).getValue());                
        } else if ( pdfObject.isLiteral() ) {
            LOGGER.info(  prefix.toString() + "pdfName=" + pdfName.getValue()  + ", object=" + pdfObjectString( pdfObject) + ", pos/count=" + 
            ((PdfLiteral)pdfObject).getPosition() + "/" + ((PdfLiteral)pdfObject).getBytesCount()  );                
        } else if ( pdfObject.isNull() ) {
            LOGGER.info(  prefix.toString() + "pdfName=" + pdfName.getValue()  + ", object=" + pdfObjectString( pdfObject) + ", value=null" );                
        } else {
            LOGGER.info(  prefix.toString() + "pdfName=" + pdfName.getValue() + ", object=" + pdfObjectString( pdfObject));                
        }     
    }
    
    /** Convert a stream of XML to a String. */
    public static String pdfStreamXMLtoString( PdfStream pdfStream ) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setIgnoringElementContentWhitespace(true);
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            ByteArrayInputStream bais = new ByteArrayInputStream(pdfStream.getBytes());
            org.w3c.dom.Document doc = builder.parse(bais);
            // Do something with the document here.
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            // Replace new lines, returns, and whitespace before tags.
            String xmlString = writer.getBuffer().toString().replaceAll("\n|\r", "").replaceAll("\\s+<", "<");
            return xmlString;
        } catch (Exception e) {
            LOGGER.error("Exception parsing metadata e=" + e);
        }
        return "";
    }
    
    /** Tells what type of object this is. */
    public static String pdfObjectString( PdfObject pdfObject ) {
        String delim = ",";
       if ( null == pdfObject ) {
           return "null";
       }
       StringBuilder sb = new StringBuilder( "type=" + pdfObject.getType() );       
       if ( pdfObject.isArray() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "array");
       }
       if ( pdfObject.isBoolean() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "boolean");
       }
       if ( pdfObject.isDictionary() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "dictionary"); 
       }
       if ( pdfObject.isFlushed() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "flushed");
       }
       if ( pdfObject.isIndirect() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "indirect");
       }
       if ( pdfObject.isIndirectReference() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "indirect ref");
       }
       if ( pdfObject.isLiteral() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "literal");
       }
       if ( pdfObject.isModified() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "modified");
       }
       if ( pdfObject.isName() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "name");
       }
       if ( pdfObject.isNull() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "null");
       }
       if ( pdfObject.isNumber() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "number");
       }
       if ( pdfObject.isReleaseForbidden() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "forbidden");
       }
       if ( pdfObject.isStream() ) {
           if (sb.length() > 0  ) sb.append( delim );
           PdfName subtype = ((PdfStream)pdfObject).getAsName(PdfName.Subtype);
           if (null==subtype) {
              sb.append( "stream" );
           } else { 
              sb.append( "stream subtype=" + subtype.getValue());
           }
       }
       if ( pdfObject.isString() ) {
           if (sb.length() > 0  ) sb.append( delim );
           sb.append( "string");
       }
       return sb.toString();
    }
 }