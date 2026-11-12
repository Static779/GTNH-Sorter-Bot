package org.lwjgl.opengl;
public class GL11 {
 public static final int GL_TEXTURE_2D=0x0DE1, GL_LIGHTING=0x0B50, GL_DEPTH_TEST=0x0B71, GL_BLEND=0x0BE2, GL_SRC_ALPHA=0x0302, GL_ONE_MINUS_SRC_ALPHA=0x0303, GL_LINES=0x0001;
 public static void glPushMatrix(){} public static void glPopMatrix(){} public static void glTranslated(double x,double y,double z){}
 public static void glDisable(int c){} public static void glEnable(int c){} public static void glBlendFunc(int a,int b){} public static void glLineWidth(float w){}
 public static void glBegin(int m){} public static void glEnd(){} public static void glColor4ub(byte r,byte g,byte b,byte a){} public static void glVertex3d(double x,double y,double z){}
}
