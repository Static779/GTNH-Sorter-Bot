package net.minecraft.item;
public final class ItemStack { public int stackSize; public Item getItem(){return null;} public int getItemDamage(){return 0;} public int getMaxStackSize(){return 64;}
 public ItemStack copy(){return this;} public String getDisplayName(){return "";} public static boolean areItemStackTagsEqual(ItemStack a, ItemStack b){return true;}  public boolean getHasSubtypes(){return false;}
}
