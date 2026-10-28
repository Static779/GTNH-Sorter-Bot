package net.minecraftforge.common.config;
public class Configuration { public static final String CATEGORY_GENERAL="general"; public Configuration(java.io.File f){}
 public int getInt(String n,String c,int d,int min,int max,String com){return d;} public String getString(String n,String c,String d,String com){return d;}
 public float getFloat(String n,String c,float d,float min,float max,String com){return d;}
 public Property get(String c, String k, int d){return new Property();}
 public ConfigCategory getCategory(String c){return new ConfigCategory();}
 public boolean getBoolean(String n,String c,boolean d,String com){return d;} public boolean hasChanged(){return false;} public void save(){} }
