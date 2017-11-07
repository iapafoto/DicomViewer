package javaclsimple;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import javaclsimple.gui.CLEditor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import javaclsimple.loader.DataLoader;
import javax.swing.JFrame;

/**
 *
 * @author durands
 */
public class DicomViewerTexture3D extends CLEditor {

    final static int 
        PARAM_CAM_FROM = 0,
        PARAM_CAM_TO = 1,
        PARAM_SLIDER_MIN = 2,  
        PARAM_SLIDER_MAX = 3,
        PARAM_BUFFER_3D = 4,
        PARAM_DEM_SIZE = 5,
        PARAM_IMG_OUT = 6,
        PARAM_SUB_PIXEL = 7,
        PARAM_DT = 8;

    private final static int[] demSize = new int[4];
    private final static float[] minMax = new float[2];
    private final static float[] demBuff = DataLoader.getDicom("C:\\LboxExport", 62,62,0,450,450,438,demSize, minMax);
//    private final static float[] demBuff = DataLoader.getDicom("C:\\LboxExport", 62,62,0,450,450,438,demSize, minMax);
    //private final static float[] demBuff = DataLoader.getDicom("C:\\dicom\\1010_brain_mr_02_lee", demSize, minMax);
//    private final static float[] demBuff = DataLoader.getDicom("C:\\dicom\\Pied", demSize, minMax);
//    private final static float[] demBuff = DataLoader.getDicom("C:\\LboxExport4",demSize, minMax);
//    private final static float[] demBuff = DataLoader.getDicom("C:\\dicom\\Coeur", demSize, minMax);

    public DicomViewerTexture3D() {
        super("DicomViewerTexture3D.cl");
        
        clManager.createInputAs3DTexture(PARAM_BUFFER_3D, demBuff, demSize);
        
        initSlider1(0, 1000, 200f, 800f);  
        initSlider2(0, 1000, 200f, 800f);
        initSlider3(0, 1000, 200f, 800f);
        initSlider4(-1000, 1000, 230f, 900f);
    }
     
    @Override
    protected void updateKernelArgsAfterCompil() {
        updateKernelArgsAfterResize();
        updateKernelArgsAfterCtrlChanged();

        clManager.setImageInOnArg(PARAM_BUFFER_3D);     
        clManager.setArg(PARAM_DEM_SIZE, demSize);
        clManager.setImageOutOnArg(PARAM_IMG_OUT, false);
    }

    @Override
    protected void updateKernelArgsAfterResize() {
        clManager.setImageOutOnArg(PARAM_IMG_OUT, false);
    }

    @Override
    protected void updateKernelArgsAfterCtrlChanged() {
            if (cursor == null) {
                cursor = new Point(screen.getWidth()/2,screen.getHeight()/2);
            }
            double a2 = .5*3.14*((double)cursor.y/(double)screen.getHeight());
            double a1 = 3.14+6.28*((double)cursor.x/(double)screen.getWidth());
            double r = 1000.*getZoom(); 

            float[] ra = float4(demSize[0]*.5, demSize[1]*.5, demSize[2]*.5);
            float[] ro = float4(ra[0] + r*Math.cos(a1)*Math.sin(a2),
                                ra[1] + r*Math.cos(a2),
                                ra[2] + r*Math.sin(a1)*Math.sin(a2));
            
            clManager.setArg(PARAM_CAM_FROM, ro);
            clManager.setArg(PARAM_CAM_TO, ra);  
            clManager.setArg(PARAM_SLIDER_MIN, getSliderMins());    
            clManager.setArg(PARAM_SLIDER_MAX, getSliderMaxs());    
            clManager.setArg(PARAM_SUB_PIXEL, new float[]{0.f,0.f});
    }
    
    @Override
    public void updateKernelArgsRenderingMode(boolean antialiasing) {
        final float 
                subX = (antialiasing) ? (float)Math.random() : .5f,
                subY = (antialiasing) ? (float)Math.random() : .5f;

        clManager.setArg(PARAM_SUB_PIXEL, new float[] {subX, subY});
        clManager.setArg(PARAM_DT, antialiasing ? 1 : 0);
    }

    public static void main(String[] args) throws Exception {
        JFrame frame = new JFrame("OpenCL Editor");
        frame.setResizable(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        DicomViewerTexture3D ch = new DicomViewerTexture3D();
        ch.setPreferredSize(new Dimension(800, 600));
        frame.setLayout(new BorderLayout());
        frame.add(ch, BorderLayout.CENTER);
        frame.pack();
        frame.show();
        ch.onLoadProgram();
    }
    
}
