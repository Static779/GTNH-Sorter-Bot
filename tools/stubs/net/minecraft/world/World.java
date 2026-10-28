package net.minecraft.world;
public abstract class World { public java.util.List<net.minecraft.tileentity.TileEntity> loadedTileEntityList;
 public net.minecraft.block.Block getBlock(int x,int y,int z){return null;} public boolean blockExists(int x,int y,int z){return true;}
 @SuppressWarnings("rawtypes") public java.util.List getEntitiesWithinAABB(Class c, net.minecraft.util.AxisAlignedBB b){return null;} }
