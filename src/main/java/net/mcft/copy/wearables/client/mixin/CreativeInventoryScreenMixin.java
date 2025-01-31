package net.mcft.copy.wearables.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.mcft.copy.wearables.api.IWearablesScreen;

import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen.CreativeContainer;

import net.minecraft.item.ItemGroup;

@Environment(EnvType.CLIENT)
@Mixin(CreativeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin
	extends AbstractInventoryScreen<CreativeContainer>
	implements IWearablesScreen
{
	public CreativeInventoryScreenMixin()
		{ super(null, null, null); }
	
	@Shadow
	private static int selectedTab;
	
	@Override
	public boolean allowWearablesPopup()
		{ return (selectedTab == ItemGroup.INVENTORY.getIndex()); }
}
