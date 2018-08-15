package info.danbecker.pdfcl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.ReaderProperties;
import com.itextpdf.kernel.utils.PdfMerger;

import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.AreaBreakType;

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

    public static final String CMD_DELIM = "\\s*,\\s*"; // 0* whitespace, comma, 0* whtitespace

    protected static String verb;
    protected static String[] srcs;
    protected static String dest;
    protected static int number;
    protected static List<Integer> list;

    // Constructors
    // Runtime
    public static void main(String[] args) throws Exception {
        // Parse command line options
        parseOptions(args);

        if (null != verb && verb.length() > 0) {
            switch (verb) {
            case "create": {
                new PdfCL().createPdf(dest, number);
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
            case "reverse": {
                new PdfCL().reversePdf(srcs);
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
            number = Integer.parseInt(line.getOptionValue("number"));
            LOGGER.info("number=" + number);
        } else {
            number = 0;
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
            srcDoc.copyPagesTo(pagesToMerge, resultDoc);
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
}