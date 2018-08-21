package info.danbecker.pdfcl;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Some utilities for manipulating images
 * 
 * @author <a href="mailto://dan@danbecker.info">Dan Becker</a>
 */
public class Image {
    /** LOGGER */
    public static final Logger LOGGER = LoggerFactory.getLogger(Image.class);
 
    /** MAX_ARGB_DISTANCE is sqrt of 4 * 255^2, for example (0,0,0,0) and (255,255,255,255) */
    public static double MAX_ARGB_DISTANCE = Math.sqrt( 4.0d * Math.pow( 255.0, 2.0 ));
 
    // Constructors
    
    /** 
     * Takes source files or directory of source files, breaks them into individuals, passes them on.
     * @param srcs
     * @param dest
     * @param number is tolerance expressed as a float percentage, for example 0.05
     * @throws IOException
     */
    public static void autoCrop(String[] srcs, String dest, String baseColorARGB, String number ) throws IOException {
        // Treat dest as a path and make dirs
        File destFile = new File(dest);
        destFile.mkdirs();
        
        // Tolerance option specified as a percentage 0..1
        Float tolerance = 0.10f;
        if ( number != null ) {
            tolerance = Float.parseFloat(number);
        }

        // Copy 
        for (String src : srcs) {
            File srcFile = new File ( src );
            if ( srcFile.canRead() ) {
                if ( srcFile.isDirectory() ) {
                    ArrayList<File> srcFiles = new ArrayList<File>(Arrays.asList(srcFile.listFiles()));
                    for ( File oneFile : srcFiles ) {
                        autoCrop( oneFile, destFile, baseColorARGB, tolerance );                        
                    }
                } else {
                    autoCrop( srcFile, destFile, baseColorARGB, tolerance );
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
    public static void autoCrop(File srcFile, File destFile, String baseColorString, float tolerance ) throws IOException {
        if (srcFile.exists() && srcFile.isFile() && srcFile.canRead()) {
            BufferedImage in = ImageIO.read(srcFile);
            LOGGER.info("Input image \"" + srcFile.getName() + "\" size=" + in.getWidth() + "x" + in.getHeight() + ", type=" + in.getType());

            Path outputPath = null;
            if ( destFile.isDirectory() ) {
                // Put string in between file name and extension.
                String srcName = srcFile.getName();
                String [] srcNameParts = srcName.split( "\\.");
                outputPath = Paths.get(destFile.toString(), srcNameParts[ 0 ] + "-c." + srcNameParts[1]  );
            } else {
                outputPath = Paths.get(destFile.toString());
            }

            int baseColor = -2;
            if ( null != baseColorString ) {
                String [] components = baseColorString.split("\\,");
                if ( components.length == 3 ) {
                    baseColor = argbInt(null, components[0], components[1], components[2]);                    
                } else if (components.length == 4){
                    baseColor = argbInt(components[0], components[1], components[2], components[3]);                    
                } else {
                    throw new IllegalArgumentException( "base color \"" + baseColorString + "\" is illegal");
                }
            }
            
            BufferedImage out = getCroppedImage( in, baseColor, tolerance );
            
            if ( null != out ) {
                LOGGER.info("Output image \"" + outputPath.toFile().getName() + "\" size=" + out.getWidth() + "x" + out.getHeight() + ", type=" + out.getType());
                ImageIO.write(out, "jpg", outputPath.toFile());
            } else {
                LOGGER.info( "Input image \"" + srcFile.getName() + "\" no adjustments" );                
            }
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
     * @return a cropped BufferedImage or null for no changes
     */
    public static BufferedImage getCroppedImage(BufferedImage source, int baseColor, double tolerance) throws IOException {
        int width = source.getWidth();
        int height = source.getHeight();
        boolean imageCloseness = false; // turn on/off writing of closeness image

        // Draw an image of pixel distances.
        BufferedImage closenessImage = new BufferedImage( width, height, source.getType() );
        if ( -2 == baseColor ) {
            baseColor = calculateBaseColor( source );
        }
        LOGGER.info( "Base color=" + colorString( baseColor ));
        
        int topY = Integer.MAX_VALUE, topX = Integer.MAX_VALUE;
        int bottomY = -1, bottomX = -1;
        int topXAdjustCount = 0;
        int bottomXAdjustCount = 0;
        int topYAdjustCount = 0;
        int bottomYAdjustCount = 0;
        int inTolerance = 0;
        int outTolerance = 0;
        int pixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels++;
                // double distance = colorDistance( baseColor, source.getRGB( x, y ) );
                if (colorWithinTolerance(baseColor, source.getRGB( x, y ), tolerance)) {
                    closenessImage.setRGB(x,y, argbInt( "255", "0", "255", "0"));
                    inTolerance++;
                    if (x < topX) {
                        topX = x;
                        topXAdjustCount++;
                    }
                    if (y < topY) {
                        topY = y;
                        topYAdjustCount++;
                    }
                    if (x > bottomX) {
                        bottomX = x;
                        bottomXAdjustCount++;
                    }
                    if (y > bottomY) {
                        bottomY = y;
                        bottomYAdjustCount++;
                    }
                } else {
                    closenessImage.setRGB(x,y, argbInt( "255", "255", "0", "0"));
                    outTolerance++;
                }
            }
        }
        LOGGER.info( "Pixels/in tolerance/out tolerance=" + pixels + "/" + inTolerance + "/" + outTolerance );
        LOGGER.info( "Edge adjustments: topX=" + topX + ", topY=" + topY + ", bottomX=" + bottomX + ", bottomY=" + bottomY);
        LOGGER.info( "Edge adjustment counts: topX=" + topXAdjustCount + ", topY=" + topYAdjustCount + ", bottomX=" + bottomXAdjustCount + ", bottomY=" + bottomYAdjustCount);
        if ( 0 == topXAdjustCount && 0 == topYAdjustCount && 0 == bottomXAdjustCount && 0 == bottomYAdjustCount) {
            return null;
        }
        BufferedImage destination = new BufferedImage((bottomX - topX + 1), (bottomY - topY + 1),
                source.getType());
        destination.getGraphics().drawImage(source, 0, 0, destination.getWidth(), destination.getHeight(),
             topX, topY, bottomX+1, bottomY+1, null);

        if ( imageCloseness ) {
            ImageIO.write(closenessImage, "jpg", new File( "resources/extractImages/closenessMap.jpg"));
        }
        closenessImage.flush();

        return destination;
    }
    
    public static int calculateBaseColor( BufferedImage source ) {
        int width = source.getWidth();
        int height = source.getHeight();
        // Get our top-left pixel color as our "baseline" for cropping
        // Get all four corners and the baseColor is the "lowest distance" corner color.
        int topLeftColor = source.getRGB(0, 0);
        int topRightColor = source.getRGB(width-1, 0);
        int bottomLeftColor = source.getRGB(0,height-1);
        int bottomRightColor = source.getRGB(width-1, height-1);

        LOGGER.debug("Top left/right " + colorString( topLeftColor ) + "=>" + colorString( topRightColor ) + "=" + colorDistance( topLeftColor,topRightColor) );  
        LOGGER.debug("Left top/bottom " + colorString( topLeftColor ) + "=>" + colorString( bottomLeftColor ) + "=" + colorDistance( topLeftColor,bottomLeftColor) );  
        LOGGER.debug("Right top/bottom " + colorString( topRightColor ) + "=>" + colorString( bottomRightColor ) + "=" + colorDistance( topRightColor,bottomRightColor) );  
        LOGGER.debug("Bottom left/right " + colorString( bottomLeftColor ) + "=>" + colorString( bottomRightColor ) + "=" + colorDistance( bottomLeftColor,bottomRightColor) );  

        int lowDistance = Integer.MAX_VALUE;
        int baseColor = 0;
        
        int distanceTopLeft = (int) colorDistance( topLeftColor,topRightColor) + (int) colorDistance( topLeftColor,bottomLeftColor);
        if ( distanceTopLeft < lowDistance ) {
            lowDistance = distanceTopLeft;
            baseColor = topLeftColor;
        }
        int distanceTopRight =  (int) colorDistance( topLeftColor,topRightColor) + (int) colorDistance( topRightColor,bottomRightColor);
        if ( distanceTopRight < lowDistance ) {
            lowDistance = distanceTopRight;
            baseColor = topRightColor;
        }
        int distanceBottomLeft = (int) colorDistance( topLeftColor,bottomLeftColor) + (int) colorDistance( bottomLeftColor,bottomRightColor);
        if ( distanceBottomLeft < lowDistance ) {
            lowDistance = distanceBottomLeft;
            baseColor = bottomLeftColor;
        }
        int distanceBottomRight = (int) colorDistance( topRightColor,bottomRightColor)  + (int) colorDistance( bottomLeftColor,bottomRightColor);
        if ( distanceBottomRight < lowDistance ) {
            lowDistance = distanceBottomRight;
            baseColor = bottomRightColor;
        }
        LOGGER.info( "Color " + colorString( baseColor ) + " selected as background with min distance of " + lowDistance );
        return baseColor;        
    }

    /** Returns distance between ARGB pixels by the sqrt of the squares. 
     * @param a
     * @param b
     * @return
     */
    private static double colorDistance(int a, int b ) {
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
        return distance;
    }
    
    /**
     * Return true or false if a given int-encoded ARGB pixel is within a certain percentage distance of another.
     * @param a
     * @param b
     * @param tolerance
     * @return
     */
    private static boolean colorWithinTolerance(int a, int b, double tolerance) {
        double distance = colorDistance (a, b);
        double percentAway = distance / MAX_ARGB_DISTANCE;
        // LOGGER.info( "Percent away=" + percentAway + ", tolerance=" + tolerance);
        return (percentAway > tolerance); // strange name, but yes this reports large distances
    }
    
    /** Returns 4 tuple of ARGB in decimal. 
     * @param a
     * @return
     */
    private static String colorString(int a) {
        int aAlpha = (int) ((a & 0xFF000000) >>> 24); // Alpha level
        int aRed = (int) ((a & 0x00FF0000) >>> 16); // Red level
        int aGreen = (int) ((a & 0x0000FF00) >>> 8); // Green level
        int aBlue = (int) (a & 0x000000FF); // Blue level
        return String.format("(%d,%d,%d,%d)",aAlpha,aRed,aGreen,aBlue);
    }    
    
    /** Checks null and range and returns an int */
    public static int argbInt( String a, String r, String g, String b) {
        int color = 0;
        if ( null != a ) {
            int aInt = Integer.parseInt(a);
            if (aInt <0 || aInt > 255) {
                throw new IllegalArgumentException( "alpha value from \"" + a + "\" is not legal");
            }
            color |= aInt << 24;
        }
        int rInt = Integer.parseInt(r);
        if (rInt <0 || rInt > 255) {
            throw new IllegalArgumentException( "red value from \"" + r + "\" is not legal");
        }
        color |= rInt << 16;
        int gInt = Integer.parseInt(g);
        if (gInt <0 || gInt > 255) {
            throw new IllegalArgumentException( "green value from \"" + g + "\" is not legal");
        }
        color |= gInt << 8;
        int bInt = Integer.parseInt(b);
        if (bInt <0 || bInt > 255) {
            throw new IllegalArgumentException( "blue value from \"" + b + "\" is not legal");
        }
        color |= bInt;
        return color;
    }
}