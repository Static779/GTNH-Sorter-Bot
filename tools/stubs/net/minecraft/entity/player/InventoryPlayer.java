package net.minecraft.entity.player;
public class InventoryPlayer implements net.minecraft.inventory.IInventory { public int currentItem; public net.minecraft.item.ItemStack[] mainInventory = new net.minecraft.item.ItemStack[36];
 public net.minecraft.item.ItemStack getStackInSlot(int i){return null;} public net.minecraft.item.ItemStack getItemStack(){return null;}
 public int getSizeInventory(){return 36;} public String getInventoryName(){return "";} }
