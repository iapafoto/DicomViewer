/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javaclsimple.test;

import com.nativelibs4java.opencl.CLBuffer;
import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLEvent;
import com.nativelibs4java.opencl.CLKernel;
import com.nativelibs4java.opencl.CLMem.Usage;
import com.nativelibs4java.opencl.CLProgram;
import com.nativelibs4java.opencl.CLQueue;
import com.nativelibs4java.opencl.JavaCL;
import java.io.IOException;
import static java.lang.Math.cos;
import java.nio.ByteOrder;
import org.bridj.Pointer;
import static org.bridj.Pointer.allocateFloats;

public class JavacProba {
    

    public static void main(String[] args) throws IOException {

        CLContext context = JavaCL.createBestContext();
        CLQueue clQueue = context.createDefaultQueue();
        ByteOrder byteOrder = context.getByteOrder();

        int n = 1024;
        Pointer<Float> aPtr = allocateFloats(n).order(byteOrder);

        for (int i = 0; i < n; i++) {
            aPtr.set(i, (float)cos(i));
        }

        // Create OpenCL input/output buffers (using the native memory pointers aPtr and bPtr) :
        CLBuffer<Float> a = context.createBuffer(Usage.InputOutput, aPtr);

        // Read the program sources and compile them :
        String src = 
                    "__kernel void add_floats(global float* a, int n) {\n" +
                    "      int i = get_global_id(0);\n" +
                    "      if(i < n){\n" +
                    "      	a[i] = 2.f*a[i];\n" +
                    "       }\n" +
                    "}";

    //IOUtils.readText(new File("TutorialKernels.cl"));
        CLProgram program = context.createProgram(src).build();
        
        // Get and call the kernel :
        CLKernel addFloatsKernel = program.createKernel("add_floats");

        addFloatsKernel.setArgs(a, n);
        CLEvent evt = addFloatsKernel.enqueueNDRange(clQueue, new int[] { n });

        aPtr =  a.read(clQueue, evt); // blocks until add_floats finished
      
        // Print the first 10 output values :
        for (int i = 0; i < 10 && i < n; i++) {
            System.out.println("out[" + i + "] = " + aPtr.get(i));
        }

    }
    
    
}



