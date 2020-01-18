/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package javaclsimple.gui;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import javaclsimple.api.OpenCLWithJavaCL;
import com.nativelibs4java.util.IOUtils;
import earth.map.SynMap;
import earth.viewport.controllers.MapControllerBBox;
import fr.meteo.synopsis.client.geometry.geometry2d.Geometry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javaclsimple.api.OpenCLWithJOCL;
import javaclsimple.editor.CompileInfo;
import javaclsimple.editor.JCompilableCodeEditor;
import javaclsimple.styles.ColorTools;
import javaclsimple.styles.Palette;
import javaclsimple.styles.SliderComponent;
import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Utilities;
import org.joda.time.DateTime;

/**
 *
 * @author durands
 */
public abstract class CLEditor extends javax.swing.JPanel implements KeyListener, JCompilableCodeEditor.Compilator {

    public static final boolean CONF_LOAD_FROM_FILE = true;
    
    public static final String
                TOKEN = "ebf21458c97048c2935e1bac86607ba2",
                SERVER_HOST = "10.37.144.11",
                SERVER_PORT = "80";
    
    // TODO ajouter eclairage curseur via 
    // https://www.shadertoy.com/view/ltjGDd
    //  float sphLight( vec3 P, vec3 N, vec4 L) {
    //  vec3 oc = L.xyz - P;
    //  return max(0., L.w*dot(N,oc)/dot(oc,oc));
    //}
    public final static long SECONDE = 1000, MINUTE = 60 * SECONDE, HOUR = 60 * MINUTE, DAY = 24 * HOUR;
    protected String lastFile = null;

    protected Point cursor = new Point(0,0);
  //  protected BufferedImage lowQualityPicture = null;

    protected double zoom = 1;
    public ImagePanel screen = new ImagePanel();

  //  protected BufferedImage imageTempHQ = null;

    // Image HQ incrementale
    protected Timer timerHQ;
    protected int[] sumR, sumG, sumB;
    protected int nbCumulFrameHQ = 0;
    protected int NB_HQ_FRAMES = 128;

    
// OPENCL Stuff    
 //   public OpenCLWithJavaCL clManager = new OpenCLWithJavaCL();
    public OpenCLWithJOCL clManager = new OpenCLWithJOCL();
    
    
    public void onLoadCode() {
        //Handle open button action.
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String extension = "";

                int i = f.getName().lastIndexOf('.');
                if (i > 0) {
                    extension = f.getName().substring(i + 1);
                }
                if (extension != null) {
                    if (extension.equals("cl")) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String getDescription() {
                return "OpenCL file"; //To change body of generated methods, choose Tools | Templates.
            }
        });

        int returnVal = fc.showOpenDialog(this);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fc.getSelectedFile();
                lastFile = file.getPath();
                //This is where a real application would open the file.
                this.codePane.setText(IOUtils.readText(file));
                // log.append("Opening: " + file.getName() + "." + newline);
            } catch (IOException ex) {
                Logger.getLogger(CLEditor.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            //  log.append("Open command cancelled by user." + newline);
        }
    }

    public void onSaveCode() {
        JFileChooser fc = new JFileChooser();

        if (lastFile != null) {
            fc.setCurrentDirectory(new File(lastFile));
        }

        int returnVal = fc.showSaveDialog(this);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            BufferedWriter writer = null;
            try {
                writer = new BufferedWriter(new FileWriter(fc.getSelectedFile()));
                writer.write(codePane.getText());
            } catch (IOException e) {
            } finally {
                try {
                    if (writer != null) {
                        writer.close();
                    }
                } catch (IOException e) {
                }
            }
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_S) {
            if (lastFile != null) {
                BufferedWriter writer = null;
                try {
                    writer = new BufferedWriter(new FileWriter(lastFile));
                    writer.write(codePane.getText());
                } catch (IOException ee) {
                } finally {
                    try {
                        if (writer != null) {
                            writer.close();
                        }
                    } catch (IOException ee) {
                    }
                }
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    final public MapControllerBBox mapControllerBBox; // = new MapControllerBBox(synMap2);

    /**
     *
     * @param clFileName
     */
    public CLEditor(final String clFileName) {

        clManager.initOpenCL();
        initComponents();

        codePane.setCompilator(this);

        screenContainer.add(BorderLayout.CENTER, screen);

        try {
            // TODO read it on disk
//            String src = IOUtils.readText(CLEditor.class.getResource("../opencl/" + clFileName));
            String src = Resources.toString(CLEditor.class.getClassLoader().getResource("javaclsimple/opencl/" + clFileName), Charsets.UTF_8);

  //          String src = IOUtils.readText(CLEditor.class.getResource("javaclsimple/opencl/" + clFileName));
            codePane.setText(src);
        } catch (IOException ex) {
            Logger.getLogger(CLEditor.class.getName()).log(Level.SEVERE, null, ex);
        }

        this.sliderMinMax1.addPropertyChangeListener(SliderComponent.VALUE_CHANGING, (PropertyChangeEvent evt) -> {
            if (clManager.isKernelOk()) {
                updateKernelArgsAfterCtrlChanged();
                regenerateFast();
            }
        });

        this.sliderMinMax2.addPropertyChangeListener(SliderComponent.VALUE_CHANGING, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (clManager.isKernelOk()) {
                    updateKernelArgsAfterCtrlChanged();
                    regenerateFast();
                }
            }
        });
        this.sliderMinMax1.addPropertyChangeListener(SliderComponent.VALUE_CHANGED, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (clManager.isKernelOk()) {
                    updateKernelArgsAfterCtrlChanged();
                    regenerateHDWithReinit();
                }
            }
        });
        this.sliderMinMax2.addPropertyChangeListener(SliderComponent.VALUE_CHANGED, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (clManager.isKernelOk()) {
                    updateKernelArgsAfterCtrlChanged();
                    regenerateHDWithReinit();
                }
            }
        });
        this.sliderMinMax3.addPropertyChangeListener(SliderComponent.VALUE_CHANGING, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (clManager.isKernelOk()) {
                    updateKernelArgsAfterCtrlChanged();
                    regenerateFast();
                }
            }
        });
        this.sliderMinMax4.addPropertyChangeListener(SliderComponent.VALUE_CHANGING, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (clManager.isKernelOk()) {
                    updateKernelArgsAfterCtrlChanged();
                    regenerateFast();
                }
            }
        });
        this.sliderMinMax3.addPropertyChangeListener(SliderComponent.VALUE_CHANGED, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (clManager.isKernelOk()) {
                    updateKernelArgsAfterCtrlChanged();
                    regenerateHDWithReinit();
                }
            }
        });
        this.sliderMinMax4.addPropertyChangeListener(SliderComponent.VALUE_CHANGED, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (clManager.isKernelOk()) {
                    updateKernelArgsAfterCtrlChanged();
                    regenerateHDWithReinit();
                }
            }
        });

        sliderTime.addPropertyChangeListener(SliderComponent.VALUE_CHANGING, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (clManager.isKernelOk()) {
                    updateKernelArgsAfterTimeChanged((DateTime) evt.getNewValue());
                    regenerateFast();
                }
            }
        });

        sliderTime.addPropertyChangeListener(SliderComponent.VALUE_CHANGED, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (clManager.isKernelOk()) {
                    updateKernelArgsAfterTimeChanged((DateTime) evt.getNewValue());
                    regenerateHDWithReinit();
                }
            }
        });

        mapControllerBBox = new MapControllerBBox(synMap2);

        synMap2.setMapController(mapControllerBBox);
        synMap2.addPropertyChangeListener(SynMap.PROPERTY_MAP_BBOX_CHANGED, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (clManager.isKernelOk()) {
                    updateKernelArgsAfterMapBBoxChanged((double[]) evt.getNewValue());
                    // regenerate();
                }
            }

        });

        mapControllerBBox.addPropertyChangeListener(MapControllerBBox.PROPERTY_BBOX_CHANGING, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (clManager.isKernelOk()) {
                    updateKernelArgsAfterBBoxChanging((double[]) evt.getNewValue());
                    regenerateFast();
                }
            }
        });

        mapControllerBBox.addPropertyChangeListener(MapControllerBBox.PROPERTY_BBOX_CHANGED, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (clManager.isKernelOk()) {
                    updateKernelArgsAfterBBoxChanged((double[]) evt.getNewValue());
                }
            }

        });

        timerHQ = new Timer(200, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                CLEditor.this.regenerateHD();
                nbCumulFrameHQ++;
                if (nbCumulFrameHQ < NB_HQ_FRAMES) {
                    timerHQ.restart(); // Les 50 ms s'ecoulent apres la generation de l'image
                }
            }
        });
        timerHQ.setRepeats(false);
    }

    protected void sumPictureHQ() {
        final int[] c1 = ((DataBufferInt) screen.img.getRaster().getDataBuffer()).getData();

        final int w = (int) screen.img.getWidth(),
                  h = (int) screen.img.getHeight();
        // TODO: tout ca pourrait etre fait en Kernel
        // Accumulation
        if (nbCumulFrameHQ == 0) {
            for (int off = 0; off < w * h; off++) {
                sumR[off] = ((c1[off] >> 16) & 0xff);
                sumG[off] = ((c1[off] >> 8) & 0xff);
                sumB[off] = ((c1[off]) & 0xff);
            }
        } else {
            for (int off = 0; off < w * h; off++) {
                sumR[off] += ((c1[off] >> 16) & 0xff);
                sumG[off] += ((c1[off] >> 8) & 0xff);
                sumB[off] += ((c1[off]) & 0xff);
            }
        }

        // Preparation de l'image somme
        final int[] c2 = ((DataBufferInt) screen.img.getRaster().getDataBuffer()).getData();
        int k = nbCumulFrameHQ + 1;
        for (int off = 0; off < w * h; off++) {
            c2[off] = (0xff << 24)
                    + (((sumR[off] / k) & 0xff) << 16)
                    + (((sumG[off] / k) & 0xff) << 8)
                    + (((sumB[off] / k) & 0xff));
        }
    }

    public void regenerate() {
        clManager.regenerate(false, screen.img, this);
        screen.repaint();
    }

    public void regenerateHD() {
        clManager.regenerate(true, screen.img, this);
        sumPictureHQ();
        screen.repaint();
    }

    protected void onSavePicture() {
        final String savePath = "C:\\Users\\durands\\Desktop\\" + System.currentTimeMillis() + ".png";
        //String savePath = "/home/sebastien/Bureau/saved_" + System.currentTimeMillis() +".png";
        System.out.println("Save picture to: " + savePath);
        try {
            File outputfile = new File(savePath);
            ImageIO.write(screen.img, "png", outputfile);
        } catch (IOException ex) {
            Logger.getLogger(CLEditor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSplitPane1 = new javax.swing.JSplitPane();
        jSplitPane2 = new javax.swing.JSplitPane();
        jScrollPane2 = new javax.swing.JScrollPane();
        errorPane = new javax.swing.JTextPane();
        screenContainer = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        sliderTime = new javaclsimple.styles.SliderTimeComponent();
        sliderMinMax1 = new javaclsimple.styles.SliderMinMaxComponent();
        sliderMinMax2 = new javaclsimple.styles.SliderMinMaxComponent();
        sliderMinMax3 = new javaclsimple.styles.SliderMinMaxComponent();
        sliderMinMax4 = new javaclsimple.styles.SliderMinMaxComponent();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        toolbarPanel = new javax.swing.JPanel();
        jToggleButton1 = new javax.swing.JToggleButton();
        jToggleButton2 = new javax.swing.JToggleButton();
        jToggleButton3 = new javax.swing.JToggleButton();
        jToggleButton4 = new javax.swing.JToggleButton();
        codePane = new javaclsimple.editor.JCompilableCodeEditor();
        synMap2 = new earth.map.SynMap();

        jSplitPane1.setBackground(new java.awt.Color(0, 0, 0));
        jSplitPane1.setBorder(null);
        jSplitPane1.setDividerSize(8);
        jSplitPane1.setResizeWeight(0.5);

        jSplitPane2.setBackground(new java.awt.Color(0, 0, 0));
        jSplitPane2.setBorder(null);
        jSplitPane2.setDividerSize(8);
        jSplitPane2.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPane2.setResizeWeight(1.0);

        jScrollPane2.setBorder(null);

        errorPane.setBorder(null);
        errorPane.setForeground(new java.awt.Color(255, 102, 0));
        jScrollPane2.setViewportView(errorPane);

        jSplitPane2.setBottomComponent(jScrollPane2);

        screenContainer.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        screenContainer.setMaximumSize(new java.awt.Dimension(512, 279));
        screenContainer.setMinimumSize(new java.awt.Dimension(512, 279));
        screenContainer.setPreferredSize(new java.awt.Dimension(512, 279));
        screenContainer.setLayout(new java.awt.BorderLayout());

        jPanel2.setLayout(new javax.swing.BoxLayout(jPanel2, javax.swing.BoxLayout.PAGE_AXIS));
        jPanel2.add(sliderTime);
        jPanel2.add(sliderMinMax1);
        jPanel2.add(sliderMinMax2);
        jPanel2.add(sliderMinMax3);
        jPanel2.add(sliderMinMax4);

        screenContainer.add(jPanel2, java.awt.BorderLayout.PAGE_END);

        jSplitPane2.setLeftComponent(screenContainer);

        jSplitPane1.setLeftComponent(jSplitPane2);

        jPanel1.setLayout(new java.awt.BorderLayout());

        toolbarPanel.setBackground(new java.awt.Color(0, 0, 0));
        toolbarPanel.setDoubleBuffered(false);
        toolbarPanel.setMinimumSize(new java.awt.Dimension(100, 54));
        toolbarPanel.setPreferredSize(new java.awt.Dimension(296, 40));

        jToggleButton1.setText("Compile");
        jToggleButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton1ActionPerformed(evt);
            }
        });

        jToggleButton2.setText("Load");
        jToggleButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton2ActionPerformed(evt);
            }
        });

        jToggleButton3.setText("Save");
        jToggleButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton3ActionPerformed(evt);
            }
        });

        jToggleButton4.setText("SavePng");
        jToggleButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton4ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout toolbarPanelLayout = new javax.swing.GroupLayout(toolbarPanel);
        toolbarPanel.setLayout(toolbarPanelLayout);
        toolbarPanelLayout.setHorizontalGroup(
            toolbarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, toolbarPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jToggleButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jToggleButton4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 95, Short.MAX_VALUE)
                .addComponent(jToggleButton2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jToggleButton3)
                .addContainerGap())
        );
        toolbarPanelLayout.setVerticalGroup(
            toolbarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(toolbarPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(toolbarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jToggleButton1)
                    .addComponent(jToggleButton2)
                    .addComponent(jToggleButton3)
                    .addComponent(jToggleButton4))
                .addContainerGap(20, Short.MAX_VALUE))
        );

        jPanel1.add(toolbarPanel, java.awt.BorderLayout.SOUTH);
        jPanel1.add(codePane, java.awt.BorderLayout.CENTER);

        jTabbedPane1.addTab("tab1", jPanel1);
        jTabbedPane1.addTab("tab2", synMap2);

        jSplitPane1.setRightComponent(jTabbedPane1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 995, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jToggleButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton1ActionPerformed
        if (onLoadProgram()) {
            onScreenResize();
            if (clManager.isKernelOk()) {
                updateKernelArgsAfterCompil();
                nbCumulFrameHQ = 0;
                regenerateHDWithReinit();
            }
        }
    }//GEN-LAST:event_jToggleButton1ActionPerformed

    private void jToggleButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton2ActionPerformed
        onLoadCode();
    }//GEN-LAST:event_jToggleButton2ActionPerformed

    private void jToggleButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton3ActionPerformed
        onSaveCode();
    }//GEN-LAST:event_jToggleButton3ActionPerformed

    private void jToggleButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton4ActionPerformed
        onSavePicture();
    }//GEN-LAST:event_jToggleButton4ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javaclsimple.editor.JCompilableCodeEditor codePane;
    private javax.swing.JTextPane errorPane;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JSplitPane jSplitPane2;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JToggleButton jToggleButton1;
    private javax.swing.JToggleButton jToggleButton2;
    private javax.swing.JToggleButton jToggleButton3;
    private javax.swing.JToggleButton jToggleButton4;
    private javax.swing.JPanel screenContainer;
    protected javaclsimple.styles.SliderMinMaxComponent sliderMinMax1;
    protected javaclsimple.styles.SliderMinMaxComponent sliderMinMax2;
    protected javaclsimple.styles.SliderMinMaxComponent sliderMinMax3;
    protected javaclsimple.styles.SliderMinMaxComponent sliderMinMax4;
    private javaclsimple.styles.SliderTimeComponent sliderTime;
    private earth.map.SynMap synMap2;
    private javax.swing.JPanel toolbarPanel;
    // End of variables declaration//GEN-END:variables

    static class IntRef {

        IntRef() {
        }

        IntRef(int val) {
            v = val;
        }
        int v = 0;
    }

    static String getBetween(String txt, String s1, String s2, int[] pos, int posEnd) {
        final int[] pos2 = {pos[0]};

        final String s = getBetween(txt, s1, s2, pos2);
        if (pos2[0] > posEnd) {
            return null;
        }
        pos[0] = pos2[0];
        return s;
    }

    static String getBetweenFromLast(String txt, String s1, String s2, int[] pos) {
        int pos2 = txt.indexOf(s2, pos[0]);
        if (pos2 >= 0) {
            // pos1 += s1.length();
            int pos1 = txt.lastIndexOf(s1, pos2);
            if (pos1 >= 0) {
                pos[0] = pos2 + s2.length();
                return txt.substring(pos1 + s1.length(), pos2).trim();
            }
        }
        return null;
    }

    static String getBetween(String txt, String s1, String s2, int[] pos) {
        int pos1 = txt.indexOf(s1, pos[0]);
        if (pos1 >= 0) {
            pos1 += s1.length();
            int pos2 = txt.indexOf(s2, pos1);
            if (pos2 >= 0) {
                pos[0] = pos2 + s2.length();
                return txt.substring(pos1, pos2).trim();
            }
        }
        return null;
    }

    public void regenerateHDWithReinit() {
        int size = (int) (screen.img.getWidth() * screen.img.getHeight());

        // Reinitialisation des accumulateurs
        if (sumR == null || sumR.length != size) {
            sumR = new int[size];
            sumG = new int[size];
            sumB = new int[size];
        }
        nbCumulFrameHQ = 0;

        regenerateHD();
      //  regenerate();
        timerHQ.restart();
    }

    protected void regenerateFast() {
        timerHQ.stop();
        regenerate();
    }

    protected abstract void updateKernelArgsAfterCompil();

    protected abstract void updateKernelArgsAfterResize();

    protected abstract void updateKernelArgsAfterCtrlChanged();

    protected void updateKernelArgsAfterTimeChanged(DateTime t) {
    }

    protected void updateKernelArgsAfterBBoxChanging(double[] bbox) {
    }

    protected void updateKernelArgsAfterMapBBoxChanged(double[] bbox) {
    }

    protected void postProcessPicture(final BufferedImage img) {
    }

    public void updateKernelArgsRenderingMode(boolean antialiasing) {
    }

    protected void updateKernelArgsAfterBBoxChanged(double[] d) {
        updateKernelArgsAfterBBoxChanging(d);
        regenerateHDWithReinit();
    }

    public static int getRow(int pos, JTextPane editor) {
        int rn = (pos == 0) ? 1 : 0;
        try {
            int offs = pos;
            while (offs > 0) {
                offs = Utilities.getRowStart(editor, offs) - 1;
                rn++;
            }
        } catch (BadLocationException e) {
        }
        return rn;
    }

    public static int getColumn(int pos, JTextPane editor) {
        try {
            return pos - Utilities.getRowStart(editor, pos) + 1;
        } catch (BadLocationException e) {
        }
        return -1;
    }

    public float getSliderValue() {
        return (float) Geometry.invMix(sliderMinMax1.getMinimum(), sliderMinMax1.getMaximum(), sliderMinMax1.getValueMin());
    }

    public float[] getSliderValues() {
        return new float[]{
            (float) Geometry.invMix(sliderMinMax1.getMinimum(), sliderMinMax1.getMaximum(), sliderMinMax1.getValueMin()),
            (float) Geometry.invMix(sliderMinMax1.getMinimum(), sliderMinMax1.getMaximum(), sliderMinMax1.getValueMax()),
            (float) Geometry.invMix(sliderMinMax2.getMinimum(), sliderMinMax2.getMaximum(), sliderMinMax2.getValueMin()),
            (float) Geometry.invMix(sliderMinMax2.getMinimum(), sliderMinMax2.getMaximum(), sliderMinMax2.getValueMax())
        };
    }

    public float[] getSliderMins() {
        return new float[]{
            (float) Geometry.invMix(sliderMinMax1.getMinimum(), sliderMinMax1.getMaximum(), sliderMinMax1.getValueMin()),
            (float) Geometry.invMix(sliderMinMax2.getMinimum(), sliderMinMax2.getMaximum(), sliderMinMax2.getValueMin()),
            (float) Geometry.invMix(sliderMinMax3.getMinimum(), sliderMinMax3.getMaximum(), sliderMinMax3.getValueMin()),
            (float) Geometry.invMix(sliderMinMax4.getMinimum(), sliderMinMax4.getMaximum(), sliderMinMax4.getValueMin())
        };
    }

    public float[] getSliderMaxs() {
        return new float[]{
            (float) Geometry.invMix(sliderMinMax1.getMinimum(), sliderMinMax1.getMaximum(), sliderMinMax1.getValueMax()),
            (float) Geometry.invMix(sliderMinMax2.getMinimum(), sliderMinMax2.getMaximum(), sliderMinMax2.getValueMax()),
            (float) Geometry.invMix(sliderMinMax3.getMinimum(), sliderMinMax3.getMaximum(), sliderMinMax3.getValueMax()),
            (float) Geometry.invMix(sliderMinMax4.getMinimum(), sliderMinMax4.getMaximum(), sliderMinMax4.getValueMax())
        };
    }

    public double getZoom() {
        return zoom;
    }

    public static float[] float4(final double v1, final double v2, final double v3) {
        return new float[]{(float) v1, (float) v2, (float) v3, 0f};
    }

    public static float[] float3(final double v1, final double v2, final double v3) {
        return new float[]{(float) v1, (float) v2, (float) v3};
    }

    public static float[] float4(final double v1, final double v2, final double v3, final double v4) {
        return new float[]{(float) v1, (float) v2, (float) v3, (float) v4};
    }

    public DateTime getSelectedDate() {
        return sliderTime.getDate();
    }

    public void initSliderTime(final Collection<Long> times) {
        final List<DateTime> availableDates = new ArrayList<>();
        Set<DateTime> special = new HashSet<>();
        for (Long t : times) {
            availableDates.add(new DateTime(t));
        }
        Collections.sort(availableDates);
        sliderTime.setValidDates(availableDates, availableDates, availableDates, special, availableDates);
    }

    public class ImagePanel extends JPanel implements PanelControllerListener {

        public BufferedImage img = null;
        public PanelController controller;

        ImagePanel() {
            super();
            controller = new PanelController(this, this);
        }

        @Override
        public void paintComponent(Graphics g) {

            g.setColor(Color.black);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (img != null) {
                g.drawImage(img, (getWidth() - img.getWidth()) / 2, (getHeight() - img.getHeight()) / 2, this);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(256, 256);
        }

        @Override
        public void onStopWheel(double zoomLevel) {
            zoom = zoomLevel;
            if (clManager.isKernelOk()) {
                updateKernelArgsAfterCtrlChanged();
                regenerateHDWithReinit();
            }
            //  throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void onWheel(double zoomLevel, Point pt) {
            zoom = zoomLevel;
            if (clManager.isKernelOk()) {
                updateKernelArgsAfterCtrlChanged();
                regenerateFast();
            }
        }

        @Override
        public void onResize(int w, int h) {
        }

        @Override
        public void onStopResize() {
            onScreenResize();
            if (clManager.isKernelOk()) {
                updateKernelArgsAfterResize();
                regenerateHDWithReinit();
            }
        }

        @Override
        public void onDragged(double dx, double dy) {
            cursor.x += dx;
            cursor.y += dy;
            if (clManager.isKernelOk()) {
                updateKernelArgsAfterCtrlChanged();
                regenerateFast();
            }
        }

        @Override
        public void onDragEnd() {
            if (clManager.isKernelOk()) {
                updateKernelArgsAfterCtrlChanged();
                regenerateHDWithReinit();
            }
        }
    }

    public void initSlider2(float min, float max, float vmin, float vmax) {
        initSlider2(min, max, vmin, vmax, false);
    }

    public void initSlider1(float min, float max, float vmin, float vmax) {
        initSlider1(min, max, vmin, vmax, false);
    }

    public void initSlider3(float min, float max, float vmin, float vmax) {
        initSlider3(min, max, vmin, vmax, false);
    }

    public void initSlider4(float min, float max, float vmin, float vmax) {
        initSlider4(min, max, vmin, vmax, false);
    }

    public void initSlider1(float min, float max, float vmin, float vmax, boolean isLogarithmic) {
        sliderMinMax1.setLimits(min, max);
        sliderMinMax1.setValues(vmin, vmax);
        sliderMinMax1.setLogarithmic(isLogarithmic);
    }

    public void initSlider2(float min, float max, float vmin, float vmax, boolean isLogarithmic) {
        sliderMinMax2.setLimits(min, max);
        sliderMinMax2.setValues(vmin, vmax);
        sliderMinMax2.setLogarithmic(isLogarithmic);
    }

    public void initSlider3(float min, float max, float vmin, float vmax, boolean isLogarithmic) {
        sliderMinMax3.setLimits(min, max);
        sliderMinMax3.setValues(vmin, vmax);
        sliderMinMax3.setLogarithmic(isLogarithmic);
    }

    public void initSlider4(float min, float max, float vmin, float vmax, boolean isLogarithmic) {
        sliderMinMax4.setLimits(min, max);
        sliderMinMax4.setValues(vmin, vmax);
        sliderMinMax4.setLogarithmic(isLogarithmic);
    }

    Color[] colors = new Color[]{
        new Color(245, 244, 242),
        new Color(224, 222, 216),
        new Color(202, 195, 184),
        new Color(186, 174, 154),
        new Color(172, 154, 124),
        new Color(170, 135, 83),
        new Color(185, 152, 90),
        new Color(195, 167, 107),
        new Color(202, 185, 130),
        new Color(211, 202, 157),
        new Color(222, 214, 163),
        new Color(232, 225, 182),
        new Color(239, 235, 192),
        new Color(225, 228, 181),
        new Color(209, 215, 171),
        new Color(189, 204, 150),
        new Color(168, 198, 143),
        new Color(148, 191, 139),
        new Color(172, 208, 165)
    };

    Double[] values = new Double[]{
        3000.,
        2600.,
        2200.,
        1800.,
        1600.,
        1400.,
        1200.,
        1000.,
        800.,
        700.,
        600.,
        500.,
        400.,
        300.,
        200.,
        150.,
        100.,
        50.,
        0.
    };

    final Palette demPalette = new Palette(reverse(colors), reverse(values));

    public static <T> T[] reverse(final T[] validData) {
        for (int i = 0; i < validData.length / 2; i++) {
            T temp = validData[i];
            validData[i] = validData[validData.length - i - 1];
            validData[validData.length - i - 1] = temp;
        }
        return validData;
    }

    public static BufferedImage toPaletteImg(Palette palette, int nb) {
        BufferedImage img = new BufferedImage(nb + 1, 1, BufferedImage.TYPE_INT_ARGB);
        Double tmin = palette.getValues()[0];
        Double tmax = palette.getValues()[palette.getValues().length - 1];
        long valMem = -100;
        for (int index = 0; index <= nb; index++) {
            final double value = tmin + index * ((tmax - tmin) / nb);
            Color col = ColorTools.gradiant(palette.getValues(), palette.getColors(), value);
            img.setRGB(index, 0, col.getRGB());
        }
        File outputfile = new File("demPalette.png");
        try {
            ImageIO.write(img, "png", outputfile);
        } catch (IOException ex) {
            Logger.getLogger(CLEditor.class.getName()).log(Level.SEVERE, null, ex);
        }
        return img;
    }

    public boolean onLoadProgram() {
        String codeSrc = codePane.getText();
        String[] error = {""};
        if (clManager.createProgram(codeSrc, error)) {
            clManager.createKernels();
            this.updateKernelArgsAfterCompil();
            return true;
        } else {
            final Map<Integer, List<CompileInfo>> errors = displayError(codeSrc, error[0]);
        }
        return false;
    }

    @Override
    public Map<Integer, List<CompileInfo>> buildProgram(final String codeSrc) {
        errorPane.setText("");
        String[] error = {""};
        if (clManager.createProgram(codeSrc, error)) {
            errorPane.setForeground(Color.green);
            errorPane.setText("OK");
            toolbarPanel.setBackground(Color.black);
            return null;
        } else {
            return displayError(codeSrc, error[0]);
        }
    }
    
    private Map<Integer, List<CompileInfo>> displayError(String src, String errorMsg) {                 
        String[] lines = errorMsg.split("\n");
        errorPane.setForeground(Color.orange);
        errorPane.setText(errorMsg);
        Map<Integer, List<CompileInfo>> lstInfo = new LinkedHashMap<>();
        List<CompileInfo> lst;
        int kind = -1;
        String[] infoKind = {"error", "warning", "note"};
        String includeFile;
        for (String line : lines) {
            int pos = -1;
            for (int i = 0; i < infoKind.length; i++) {
                pos = line.lastIndexOf(infoKind[i] + ":");
                if (pos > 0) {
                    kind = i;
                    break;
                }
            }
            if (kind >= 0) {
                int p2 = line.lastIndexOf(":", pos);
                int p1 = line.lastIndexOf(":", p2 - 1);
                int p0 = line.lastIndexOf(":", p1 - 1);

                try {
                    // TODO parse line like:     C:\Users\durands\.javacl\includes\includes1481186168803523593\javaclsimple/opencl/Interpol.cl:43:62: note: passing argument to parameter 'buf' here"
                    int lineId = Integer.parseInt(line.substring(p0 + 1, p1));
                    int chr = Integer.parseInt(line.substring(p1 + 1, p2));

                    if (line.charAt(0) >= '0' && line.charAt(0) <= '9') {
                        includeFile = null;
                    } else {
                        includeFile = line.substring(0, p0);
                        int p4 = 0;
                        do {
                            p4 = src.indexOf("#include", p4);
                            if (p4 >= 0) {
                                String file = getBetween(src, "\"", "\"", new int[]{p4});
                                if (file != null && !file.isEmpty() && includeFile.endsWith(file)) {
                                    lineId = codePane.getLineIdOfCaret(p4);
                                    line = line.substring(p0 + 1);
                                    break;
                                }
                            }
                        } while (p4 >= 0);
                    }

                    lst = lstInfo.get(lineId);
                    if (lst == null) {
                        lst = new ArrayList<>();
                        lstInfo.put(lineId, lst);
                    }

                    lst.add(new CompileInfo(kind, lineId, chr, line, includeFile));
                } catch (Exception e2) {
                    int error = 1;
                }
            }
        }

        if (!lstInfo.isEmpty()) {
            toolbarPanel.setBackground(Color.red);
        }

        return lstInfo;
    }
  
    public void onScreenResize() {
        final int width = (screen.getWidth() / 64) * 64,
                height = (screen.getHeight() / 64) * 64;

        if (screen.img == null || screen.img.getWidth() != width || screen.img.getHeight() != height) {
            screen.img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
    //    if (imageTempHQ == null || imageTempHQ.getWidth() != width || imageTempHQ.getHeight() != height) {
    //        imageTempHQ = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    //    }

        clManager.onScreenResize(screen.img);//, imageTempHQ);
    }
    
    
    public static float[] getDataFract(final float[] dataBBox, final double[] bboxd) {
        return new float[] {
                Geometry.clamp((float) Geometry.invMix(dataBBox[0], dataBBox[2], bboxd[0]), 0.f, 1.f),
                // Y are in inverse order (picture vs bbox) => fractData = 1.f - fractBBox
                1.f-Geometry.clamp((float) Geometry.invMix(dataBBox[1], dataBBox[3], bboxd[3]), 0.f, 1.f),
                Geometry.clamp((float) Geometry.invMix(dataBBox[0], dataBBox[2], bboxd[2]), 0.f, 1.f),
                1.f-Geometry.clamp((float) Geometry.invMix(dataBBox[1], dataBBox[3], bboxd[1]), 0.f, 1.f)
                
        };
    }
}
