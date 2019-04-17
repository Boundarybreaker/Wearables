package net.mcft.copy.wearables.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.platform.GlStateManager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.mcft.copy.wearables.api.IWearablesEntity;
import net.mcft.copy.wearables.api.IWearablesSlot;
import net.mcft.copy.wearables.api.WearablesAPI;
import net.mcft.copy.wearables.api.WearablesRegion;
import net.mcft.copy.wearables.api.WearablesSlotType;
import net.mcft.copy.wearables.client.mixin.IContainerScreenAccessor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.ContainerScreen;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.Element;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GuiLighting;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.container.Slot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

@Environment(EnvType.CLIENT)
public class WearablesRegionPopup extends DrawableHelper implements Drawable, Element
{
	public static final GuiTexture REGION_TEX = new GuiTexture("region", 64);
	public static final int SLOT_SIZE = 18;
	public static final int Z_LEVEL = 300;
	
	
	private final List<IWearablesSlot> _slots = new ArrayList<>();
	
	public final IContainerScreenAccessor<?> screen;
	public final WearablesRegion region;
	public final Slot originSlot;
	public final int originX, originY;
	public final int centerSlot;
	public final int x, y;
	
	public boolean isVisible = false;
	
	private Set<WearablesSlotType> _highlightedSlots = new HashSet<>();
	
	
	public WearablesRegionPopup(ContainerScreen<?> screen, WearablesRegion region)
	{
		this.screen = (IContainerScreenAccessor<?>)screen;
		this.region = region;
		
		String slotHint = region.getContainerSlotHint();
		if (slotHint != null) {
			this.originSlot = screen.getContainer().slotList.stream()
				.filter(slot -> (slot.getMaxStackAmount() == 1)
				             && (slot.getBackgroundSprite() != null)
				             && slot.getBackgroundSprite().contains(slotHint))
				.findFirst().orElseThrow(() -> new IllegalStateException());
			this.originX = this.originSlot.xPosition - 1;
			this.originY = this.originSlot.yPosition - 1;
		} else {
			this.originSlot = null;
			this.originX    = -10000;
			this.originY    = -10000;
		}
		
		((IWearablesEntity)MinecraftClient.getInstance().player)
			.getWearablesSlots(region).forEach(this._slots::add);
		this._slots.sort(Comparator.comparing(IWearablesSlot::getOrder)
		                          .thenComparing(slot -> slot.getSlotType().fullName));
		int centerIndex = -1;
		int minAbsOrder = Integer.MAX_VALUE;
		for (int i = 0; i < this._slots.size(); i++) {
			int order = Math.abs(this._slots.get(i).getOrder());
			if (order >= minAbsOrder) break;
			centerIndex = i;
			minAbsOrder = order;
		}
		
		this.centerSlot = centerIndex;
		this.x = this.originX - 4 - SLOT_SIZE * centerIndex;
		this.y = this.originY - 4;
	}
	
	
	public int getX() { return this.x; }
	public int getY() { return this.y; }
	public int getWidth() { return 8 + SLOT_SIZE * this._slots.size(); }
	public int getHeight() { return 8 + SLOT_SIZE; }
	
	
	public boolean isWithinBounds(double pointX, double pointY)
	{	
		return (isVisible && isWithinScreenSpace(
			getX(), getY(), getWidth(), getHeight(), pointX, pointY));
	}
	
	/** Returns if the specified point is within the bounds of this popup, but
	 *  not over the origin slot. This allows the slot to still be interacted with. */
	@Override
	public boolean isMouseOver(double pointX, double pointY)
	{
		return isWithinBounds(pointX, pointY)
		    && ((this.originSlot == null) || !isOverOriginBounds(pointX, pointY));
	}
	
	private boolean isOverOriginBounds(double pointX, double pointY)
		{ return isWithinScreenSpace(this.originX, this.originY, SLOT_SIZE, SLOT_SIZE, pointX, pointY); }
	
	private boolean isWithinScreenSpace(int x, int y, int width, int height, double pointX, double pointY)
		{ return isWithinGlobalSpace(x + screen.getLeft(), y + screen.getTop(), width, height, pointX, pointY); }
	
	private boolean isWithinGlobalSpace(int x, int y, int width, int height, double pointX, double pointY)
		{ return ((pointX >= x) && (pointX < x + width) && (pointY >= y) && (pointY < y + height)); }
	
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if (!isMouseOver(mouseX, mouseY)) return false;
		int y = (int)mouseY - (screen.getTop() + getY() + 4);
		if ((y < 0) || (y >= SLOT_SIZE)) return false;
		int slotIndex = ((int)mouseX - (screen.getLeft() + getX() + 4)) / SLOT_SIZE;
		if ((slotIndex < 0) || (slotIndex >= this._slots.size())) return false;
		IWearablesSlot slot = this._slots.get(slotIndex);
		
		if (!slot.canUnequip()) return false;
		PlayerInventory inventory = MinecraftClient.getInstance().player.inventory;
		ItemStack cursorStack     = inventory.getCursorStack();
		ItemStack currentEquipped = slot.get();
		if (cursorStack.isEmpty() && currentEquipped.isEmpty()) return false;
		if (!slot.canEquip(cursorStack)) return false;
		
		// FIXME: Handle ItemStacks with amount > 1 properly.
		inventory.setCursorStack(currentEquipped);
		slot.set(cursorStack);
		
		return true;
	}
	
	
	public void update(int mouseX, int mouseY)
	{
		if (isVisible && !isWithinBounds(mouseX, mouseY))
			isVisible = false;
		
		if (!isVisible && isOverOriginBounds(mouseX, mouseY) && !screen.method_19355(mouseX, mouseY).isPresent())
			isVisible = true;
		
		// TODO: This seems expensive. Only call "getValidSlots" once globally when the stack changes.
		ItemStack cursorOrFocusedStack = MinecraftClient.getInstance()
			.player.inventory.getCursorStack();
		if (cursorOrFocusedStack.isEmpty() && (screen.getFocusedSlot() != null))
			cursorOrFocusedStack = screen.getFocusedSlot().getStack();
		
		_highlightedSlots.clear();
		WearablesAPI.getValidSlots(cursorOrFocusedStack).stream()
			.filter(slotType -> (slotType.region == region))
			.forEach(_highlightedSlots::add);
	}
	
	
	@Override
	public void render(int mouseX, int mouseY, float tickDelta)
	{
		if (this.isVisible) {
			int x = screen.getLeft() + getX();
			int y = screen.getTop()  + getY();
			
			GuiLighting.enableForItems();
			GlStateManager.disableLighting();
			REGION_TEX.bind();
			REGION_TEX.drawBordered(x, y, getWidth(), getHeight(),
			                        0, 0, Z_LEVEL, 4, 30, 30, 2, false);
			
			ClientPlayerEntity player = MinecraftClient.getInstance().player;
			
			for (int i = 0; i < this._slots.size(); i++) {
				IWearablesSlot slot = this._slots.get(i);
				if ((this.originSlot == null) || (i != this.centerSlot)) {
					int xx = x + 4 + i * SLOT_SIZE;
					int yy = y + 4;
					REGION_TEX.bind();
					drawSlot(xx, yy);
					
					ItemStack stack = slot.get();
					if (!stack.isEmpty()) {
						ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
						TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
						GlStateManager.enableDepthTest();
						itemRenderer.zOffset = 200.0F;
						itemRenderer.renderGuiItem(player, stack, xx + 1, yy + 1);
						itemRenderer.renderGuiItemOverlay(textRenderer, stack, xx + 1, yy + 1, null);
						itemRenderer.zOffset = 0.0F;
					}
					
					if (isWithinGlobalSpace(xx, yy, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
						GlStateManager.disableLighting();
						GlStateManager.disableDepthTest();
						GlStateManager.colorMask(true, true, true, false);
						fillGradient(xx + 1, yy + 1, xx + SLOT_SIZE - 1, yy + SLOT_SIZE - 1, -2130706433, -2130706433);
						GlStateManager.colorMask(true, true, true, true);
						GlStateManager.enableLighting();
						GlStateManager.enableDepthTest();
					}
				}
				
				if (this._highlightedSlots.contains(slot.getSlotType())) {
					GlStateManager.enableBlend();
					REGION_TEX.bind();
					REGION_TEX.drawQuad(x + 3 + i * SLOT_SIZE, y + 3,
					                    20, 20, 25, 31, Z_LEVEL);
					GlStateManager.disableBlend();
				}
			}
		} else if (!this._highlightedSlots.isEmpty()) {
			GlStateManager.enableBlend();
			REGION_TEX.bind();
			REGION_TEX.drawQuad(screen.getLeft() + this.originX - 1,
			                    screen.getTop()  + this.originY - 1,
			                    20, 20, 25, 31, Z_LEVEL - (this.isVisible ? 0 : 50));
			GlStateManager.disableBlend();
		}
	}
	
	private void drawSlot(int x, int y)
		{ REGION_TEX.drawQuad(x, y, SLOT_SIZE, SLOT_SIZE, 6, 32, Z_LEVEL); }
}