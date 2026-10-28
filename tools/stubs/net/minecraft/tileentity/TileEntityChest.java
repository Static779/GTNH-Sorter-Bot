package net.minecraft.tileentity;
public class TileEntityChest extends TileEntity implements net.minecraft.inventory.IInventory { public int getSizeInventory(){return 27;} public String getInventoryName(){return "container.chest";} public net.minecraft.item.ItemStack getStackInSlot(int i){return null;} }
