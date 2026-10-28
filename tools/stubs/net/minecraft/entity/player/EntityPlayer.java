package net.minecraft.entity.player;
public abstract class EntityPlayer extends net.minecraft.entity.EntityLivingBase implements net.minecraft.command.ICommandSender {
 public InventoryPlayer inventory; public net.minecraft.inventory.Container inventoryContainer; public net.minecraft.inventory.Container openContainer;
 public PlayerCapabilities capabilities; public void addChatMessage(net.minecraft.util.IChatComponent c){}
 public void closeScreen(){} public net.minecraft.item.ItemStack getHeldItem(){return null;} public float getEyeHeight(){return 0.12F;} }
