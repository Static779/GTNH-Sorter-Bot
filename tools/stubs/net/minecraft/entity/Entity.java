package net.minecraft.entity;
public abstract class Entity { public double posX, posY, posZ; public float rotationYaw, rotationPitch; public boolean onGround; public boolean isDead;
 public final net.minecraft.util.AxisAlignedBB boundingBox = null; public boolean isSneaking(){return false;} public void setSprinting(boolean b){} }
