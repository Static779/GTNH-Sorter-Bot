package cpw.mods.fml.client;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
public interface IModGuiFactory {
 void initialize(Minecraft minecraftInstance);
 Class<? extends GuiScreen> mainConfigGuiClass();
 Set<RuntimeOptionCategoryElement> runtimeGuiCategories();
 RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element);
 class RuntimeOptionCategoryElement { public final String parent = null, child = null; }
 interface RuntimeOptionGuiHandler {}
}
