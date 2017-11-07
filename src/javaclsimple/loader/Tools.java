/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package javaclsimple.loader;

import javaclsimple.loader.DataLoader;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 *
 * @author sebastien
 */
public class Tools {
    public static void main(String[] args) throws Exception {
        String base = "/home/sebastien/Desktop/Radar3D/";
        
        int width = 401;
        int height = 441;
        
        BufferedImage tmp, all = new BufferedImage(width*4, height*4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = all.createGraphics();
        for (int i=0; i<4; i++) {
            for (int j=0; j<4; j++) {
             //  tmp = ImageIO.read(Tools.class.getClassLoader().getResource("layer_" + (500+(j*4+i)*500) + ".tiff"));
                tmp = DataLoader.getResourceImg("layer_" + (500+(j*4+i)*500) + ".png");
                g2.drawImage(tmp, i*width,j*height, null);
            }
        }
        g2.dispose();
        ImageIO.write(all, "png", new File("allInOne.png"));

    }
}
