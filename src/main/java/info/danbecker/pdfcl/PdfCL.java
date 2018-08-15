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

public class PdfCL {
    /** LOGGER */
    public static final Logger LOGGER = LoggerFactory.getLogger(PdfCL.class);

    public static final String[] SRC = { "resources/input.pdf" };
    public static final String DEST = "resources/output.pdf";

    public static final String CMD_DELIM = "\"\\\\s*,\\\\s*\""; // 0* whitespace, comma, 0* whtitespace

    protected static String verb;
    protected static String[] src;
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
            case "append": {
                new PdfCL().appendPdf(src, dest, list);
                break;
            }
            case "reverse": {
                new PdfCL().reversePdf(src);
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
            src = option.split(CMD_DELIM);
            LOGGER.info("src=" + src);
        } else {
            src = SRC;
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
     * Creates a dummy PDF
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

    public void appendPdf(String[] srcs, String dest, List<Integer> pagesToMerge) throws IOException {
        mkdirs(dest);
        PdfDocument pdfDest = new PdfDocument(new PdfWriter(dest));

        for (String src : srcs) {
            PdfDocument pdfSrc = new PdfDocument(new PdfReader(src));
            pdfSrc.copyPagesTo(pagesToMerge, pdfDest);
            pdfSrc.close();
        }

        pdfDest.close();
    }

    // public void appendPdf(String src, String dest, List<Integer> pagesToMerge)
    // throws IOException {
    // File file = new File(dest);
    // file.getParentFile().mkdirs();
    //
    //
    // PdfDocument pdfOriginalPages = null;
    // if (file.exists() && file.length() > 0) {
    // pdfOriginalPages = new PdfDocument(new PdfReader(dest));
    // }
    // PdfDocument pdfDest = new PdfDocument(new PdfWriter(dest));
    // // PdfDocument pdfDest = new PdfDocument(new PdfReader(dest), new
    // PdfWriter(dest));
    // PdfDocument pdfSrc = new PdfDocument(new PdfReader(src));
    //
    // PdfMerger merger = new PdfMerger(pdfDest); // cannot append to existing doc
    // if (file.exists() && file.length() > 0) {
    // merger.merge(pdfDest, 1, pdfOriginalPages.getNumberOfPages());
    // }
    // merger.merge(pdfSrc, pagesToMerge);
    // // pdfSrc.copyPagesTo( pagesToMerge, pdfDest);
    //
    // if (file.exists() && file.length() > 0) {
    // pdfOriginalPages.close();
    // }
    // pdfSrc.close();
    // pdfDest.close();
    // }

    protected void reversePdf(String[] srcs) throws Exception {
        for (String src : srcs) {
            File srcFile = new File(src);
            if (fileReadable(dest)) {
                LOGGER.info("File \"" + srcFile + "\" exists=" + srcFile.exists() + ", canRead=" + srcFile.canRead() + ", length="
                        + srcFile.length());
                return;
            }
            LOGGER.info("Source file=" + src);
            byte[] byteArray = Files.readAllBytes(srcFile.toPath());
            PdfDocument srcDoc = new PdfDocument(
                    new PdfReader(new RandomAccessSourceFactory().createSource(byteArray), new ReaderProperties()));
            PdfDocument resultDoc = new PdfDocument(new PdfWriter(dest));
            resultDoc.initializeOutlines();

            List<Integer> pages = new ArrayList<>();
            int numPages = srcDoc.getNumberOfPages();
            LOGGER.info("NumPages=" + numPages);
            for (int pagei = numPages; pagei > 0; pagei--) {
                pages.add(pagei);
            }
            LOGGER.info("Pages=" + pages);
            srcDoc.copyPagesTo(pages, resultDoc);

            resultDoc.close();
            srcDoc.close();
        }
    }

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