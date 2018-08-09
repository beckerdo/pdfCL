package info.danbecker.pdfcl;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.kernel.pdf.IPdfPageExtraCopier;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.AreaBreakType;

public class PdfCL {
    /** LOGGER */
    public static final Logger LOGGER = LoggerFactory.getLogger(PdfCL.class);
    
    public static final String SRC = "resources/input.pdf";
    public static final String SRC2 = "resources/input2.pdf";
    public static final String DEST = "resources/output.pdf";
    
    protected static String verb;
    protected static String src;
    protected static String src2;
    protected static String dest;
    
    // Constructors
    // Runtime
    public static void main(String[] args) throws Exception {
        // Parse command line options
        parseOptions(args);
        
        if ( null != verb && verb.length() > 0) {
            switch ( verb ) {
                case "create": {
                    new PdfCL().createPdf( dest, 10 );
                    break;
                }
                default: {
                    LOGGER.info("verb \"" + verb + "\" is unknown");                   
                }
        
            }
        }
        LOGGER.info( "exiting" );
    }

    /** Command line options for this application. */
    public static void parseOptions(String[] args) throws ParseException, IOException {
        // Parse the command line arguments
        final Options options = new Options();
        // Use dash with shortcut (-h) or -- with name (--help).
        options.addOption("h", "help", false, "print the command line options");
        options.addOption("v", "verb", true, "action to perform");
        options.addOption("s", "src", true, "input PDF file");
        options.addOption("s2", "src2", true, "input PDF file");
        options.addOption("d", "dest", true, "output PDF file");

        final CommandLineParser cliParser = new DefaultParser();
        final CommandLine line = cliParser.parse(options, args);
        // line.getArgList(); // Retrieve any left-over non-recognized options and arguments
        
        List<String> list =  line.getArgList();
        LOGGER.info("arg count=" + list.size());            
        for (String arg : line.getArgList()) {
            LOGGER.info("arg=" + arg);            
        }
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
            src = line.getOptionValue("src");
        } else {
            src = SRC;
        }
        LOGGER.info("src=" + src);
        if (line.hasOption("src2")) {
            src2 = line.getOptionValue("src2");
        } else {
            src2 = SRC2;
        }
        LOGGER.info("src2=" + src2);
        if (line.hasOption("dest")) {
            dest = line.getOptionValue("dest");
        } else {
            dest = DEST;
        }
        LOGGER.info("dest=" + dest);
    }
    
    /** 
     * Creates a dummy PDF
     * @param dest output file name
     * @param numPages number of pages
     * @throws IOException
     */
    public void createPdf(String dest, int numPages) throws IOException {
        File file = new File(dest);
        file.getParentFile().mkdirs();
        
        // Initialize PDF writer
        PdfWriter writer = new PdfWriter(dest);
 
        // Initialize PDF document
        PdfDocument pdf = new PdfDocument(writer);
 
        // Initialize document
        Document document = new Document(pdf);
 
        // Add pages to the document
        AreaBreak areaBreak = new AreaBreak(AreaBreakType.NEXT_PAGE);
        for ( int i = 0; i < numPages; i++ ) {
            document.add(new Paragraph( "Page" + Integer.toString(i + 1)));
            document.add(areaBreak);           
        }
 
        //Close document
        document.close();
        LOGGER.info( "\"" + dest + "\" created");
    }
    
    public void mergePdf(String src1, String src2, String dest) throws IOException {
        PdfDocument pdfDest= new PdfDocument(new PdfWriter(dest));
        PdfDocument pdfSrc1 = new PdfDocument(new PdfReader(src1));
        PdfDocument pdfSrc2 = new PdfDocument(new PdfReader(src2));
        
        PdfMerger merger = new PdfMerger(pdfDest);
        merger.merge(pdfSrc1, 1, 1);
        merger.merge(pdfSrc2, 1, pdfSrc2.getNumberOfPages());
        
        pdfDest.close();
        pdfSrc1.close();
        pdfSrc2.close();
    }
    
    public void appendPdf(String src1, String src2, String dest) throws IOException {
        PdfDocument pdfDest = new PdfDocument(new PdfReader(src1), new PdfWriter(dest));
        PdfDocument cover = new PdfDocument(new PdfReader(src2));
        // cover.copyPagesTo(1, 1, pdfDest, 1, new PdfPageFormCopier());
        // cover.copyPagesTo(1, 1, pdfDest, 1, new IPdfPageExtraCopier());
        // cover.copyPagesTo(1, 1, pdfDest, 1, new PdfPage());
        cover.close();
        pdfDest.close();
    }
}