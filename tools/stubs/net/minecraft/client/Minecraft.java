package net.minecraft.client;
public class Minecraft { public net.minecraft.client.entity.EntityClientPlayerMP thePlayer; public net.minecraft.client.multiplayer.WorldClient theWorld;
 public net.minecraft.client.gui.GuiScreen currentScreen; public net.minecraft.client.multiplayer.PlayerControllerMP playerController;
 public net.minecraft.client.settings.GameSettings gameSettings; public java.io.File mcDataDir;
 public static Minecraft getMinecraft(){return null;} public void displayGuiScreen(net.minecraft.client.gui.GuiScreen s){} }
