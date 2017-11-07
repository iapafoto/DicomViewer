/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package javaclsimple.loader;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import javaclsimple.loader.dicom.DicomReader;

/**
 *
 * @author durands
 */
public final class DataLoader {
    
    public static ImageIcon getResourceIcon(String name) {
        return new ImageIcon(DataLoader.class.getClassLoader().getResource(name));
    }

    public static BufferedImage getResourceImg(final String name) {
        return toBufferedImage(getResourceIcon(name));
    }
    public static BufferedImage getImgARGB(final String name) {
        try {
            BufferedImage img = ImageIO.read(new File(name));
            if (img != null) {
                BufferedImage img2 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = (Graphics2D) img2.getGraphics();
                g2.drawImage(img, null, 0, 0);
                g2.dispose();
                return img2;
            }
        } catch (IOException ex) {
            Logger.getLogger(DataLoader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    

    
    
    static float[] getResourceValues3D(String allInOnepng, int i, int i0, int[] sizeOut) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    public static BufferedImage toBufferedImage(final ImageIcon icon) {
        if (icon == null) return null;
        final int w = icon.getIconWidth(), h = icon.getIconHeight();
        final BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); 
        final Graphics g = bi.createGraphics();
        icon.paintIcon(null, g, 0,0);
        g.dispose();
        return bi;
    }
    

    
    public static float[] getDicom(final String file, final int[] outSize, final float[] outMinMax) {
        final double[][][] grib = DicomReader.decodeDirToArray(new File(file));
        return to1D(grib, outSize,outMinMax);
    }

    public static float[] getDicom(final String file, 
            final int xmin, final int ymin, final int zmin, final int xmax, final int ymax, final int zmax, 
            final int[] outSize, final float[] outMinMax) {
        final double[][][] grib = DicomReader.decodeDirToArray(new File(file));
        return to1D(grib, xmin,ymin,zmin, xmax,ymax,zmax, outSize,outMinMax);
    }

 
    public static float[] to1D(final float[][] grib, final int[] outSize, final float[] outMinMax) {
        return to1D(grib, 0,0, grib[0].length, grib.length, outSize, outMinMax);
    }
    
    public static float[] to1DYX(final float[][] grib, final int[] outSize, final float[] outMinMax) {
        return to1DYX(grib, 0,0, grib[0].length, grib.length, outSize, outMinMax);
    }
    
    public static float[] to1D(final float[][][] grib, final int[] outSize, final float[] outMinMax) {
        return to1D(grib, 0,0,0, grib[0][0].length, grib[0].length, grib.length, outSize, outMinMax);
    }
    
    public static float[] to1D(final double[][][] grib, final int[] outSize, final float[] outMinMax) {
        return to1D(grib, 0,0,0, grib[0][0].length, grib[0].length, grib.length, outSize, outMinMax);
    }
    
    public static float[] to1D(final double[][][] grib, final int xmin, final int ymin, int zmin, final int xmax, final int ymax, int zmax, 
            final int[] outSize, final float[] outMinMax) {
        
        zmax = Math.min(grib.length,zmax);
        
        // Find min and max
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        final int sz = zmax-zmin, sy = ymax-ymin, sx = xmax-xmin;

        int off = 0;
        double v;
        final float[] data = new float[sx*sy*sz];
        for(int z=zmin; z<zmax; z++) {
            if (grib[z].length == 0) break;
            for(int y=ymin; y<ymax; y++) {
                for(int x=xmin; x<xmax; x++) {
                    v = grib[z][y][x];
                    data[off++] = (float)v;
                    if (v<min) min = v;
                    if (v>max) max = v;   
                }
            }
        }
        if (outMinMax != null && outMinMax.length>=2) {
            outMinMax[0] = (float)min;
            outMinMax[1] = (float)max;
        }
        if (outSize != null && outSize.length>=3) {
            outSize[0] = sx;
            outSize[1] = sy;
            outSize[2] = sz;
        }
        return data;
    }
    
    public static float[] to1D(final float[][][] grib, 
            final int xmin, final int ymin, final int zmin, 
            final int xmax, final int ymax, final int zmax, 
            final int[] outSize, final float[] outMinMax) {
        // Find min and max
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        final int sz = zmax-zmin, sy = ymax-ymin, sx = xmax-ymin;
        int off = 0;
        Float v;
        final float[] data = new float[sx*sy*sz];
        for(int z=zmin; z<zmax; z++) {
            for(int y=ymin; y<ymax; y++) {
                for(int x=xmin; x<xmax; x++) {
                    v = grib[z][y][x];
                    data[off++] = v;
                    if (v<min) min = v;
                    if (v>max) max = v;   
                }
            }
        }
        if (outMinMax != null && outMinMax.length>=2) {
            outMinMax[0] = min;
            outMinMax[1] = max;
        }
        if (outSize != null && outSize.length>=3) {
            outSize[0] = sx;
            outSize[1] = sy;
            outSize[2] = sz;
        }
        return data;
    }

    public static float[] to1D(final float[][] grib, 
            final int xmin, final int ymin, 
            final int xmax, final int ymax, 
            final int[] outSize, final float[] outMinMax) {
        // Find min and max
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        final int sy = ymax-ymin, sx = xmax-ymin;
        int off = 0;
        Float v;
        final float[] data = new float[sx*sy];
        for(int y=ymin; y<ymax; y++) {
            for(int x=xmin; x<xmax; x++) {
                v = grib[y][x];
                data[off++] = v;
                if (v<min) min = v;
                if (v>max) max = v;   
            }
        }
        
        if (outMinMax != null && outMinMax.length>=2) {
            outMinMax[0] = min;
            outMinMax[1] = max;
        }
        if (outSize != null && outSize.length>=2) {
            outSize[0] = sx;
            outSize[1] = sy;
        }
        return data;
    }
    
    public static float[] to1DYX(final float[][] grib, final int xmin, final int ymin, final int xmax, final int ymax, final int[] outSize, final float[] outMinMax) {
        // Find min and max
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        final int sy = ymax-ymin, sx = xmax-ymin;
        int off = 0;
        Float v;
        final float[] data = new float[sx*sy];
        for(int x=xmax-1; x>=xmin; x--) {
            for(int y=ymin; y<ymax; y++) {
                v = grib[y][x];
                data[off++] = v;
                if (v<min) min = v;
                if (v>max) max = v;   
            }
        }
        
        if (outMinMax != null && outMinMax.length>=2) {
            outMinMax[0] = min;
            outMinMax[1] = max;
        }
        if (outSize != null && outSize.length>=2) {
            outSize[0] = sy;
            outSize[1] = sx;
        }
        return data;
    }
    
    public static float[] decale(float[] buf, float add, float mult, float[] out) {
        for (int i=0; i<buf.length; i++) {
            out[i] = (buf[i]+add)*mult; 
        }
        return buf;
    }
    
    public static float[] mapValues(float[] buf, float min, float max, float[] out) {
        float[] minMax = new float[2];
        getMinMax(buf, minMax);
        return decale(buf, min-minMax[0], max/(minMax[1]-minMax[0]), out);
    }
    
    public static void getMinMax(float[] demBuff, float[] outMinMax) {
        if (outMinMax != null && outMinMax.length>=2) {
            float v, min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for(int off=0; off<demBuff.length; off++) {
                v = demBuff[off];
                if (v<min) min = v;
                if (v>max) max = v;   
            }
            outMinMax[0] = min;
            outMinMax[1] = max;
        }
    }
   

 

}


    
