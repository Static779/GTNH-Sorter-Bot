package net.minecraftforge.common.config;
public class Property { public enum Type { STRING, INTEGER, BOOLEAN, DOUBLE, COLOR, MOD_ID }
 public int getInt(){return 0;} public void set(int v){} public void set(boolean v){} public void set(double v){} public void set(String v){}
 public Type getType(){return null;} public String getString(){return null;} public String[] getStringList(){return null;}
 public double getDouble(double d){return d;} public Property setLanguageKey(String k){return this;} }
