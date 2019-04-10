package net.mcft.copy.wearables.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.mojang.blaze3d.platform.GlStateManager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.mcft.copy.wearables.api.WearablesAPI;
import net.mcft.copy.wearables.api.WearablesRegion;
import net.mcft.copy.wearables.api.WearablesSlot;
import net.mcft.copy.wearables.client.mixin.IContainerScreenAccessor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.ContainerScreen;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.Element;
import net.minecraft.client.render.GuiLighting;
import net.minecraft.container.Slot;
import net.minecraft.item.ItemStack;

@Environment(EnvType.CLIENT)
public class WearablesRegionPopup extends DrawableHelper implements Drawable, Element
{
	public static final GuiTexture REGION_TEX = new GuiTexture("region", 64);
	public static final int SLOT_SIZE = 18;
	public static final int Z_LEVEL = 300;
	
	
	private final List<WearablesSlot> slots = new ArrayList<>();
	
	public final IContainerScreenAccessor<?> screen;
	public final WearablesRegion region;
	public final Slot originSlot;
	public final int originX, originY;
	public final int x, y;
	
	public boolean isVisible = false;
	
	private boolean highlight = false;
	// private Set<WearablesSlot> highlightedSlots = new HashSet<>();
	
	
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
		
		region.getChildren().forEach(this::addSlotOrChildren);
		this.slots.sort(Comparator.comparing(WearablesSlot::getOrder)
		                          .thenComparing(slot -> slot.fullName));
		int centerIndex = -1;
		int minAbsOrder = Integer.MAX_VALUE;
		for (int i = 0; i < this.slots.size(); i++) {
			int order = Math.abs(this.slots.get(i).getOrder());
			if (order >= minAbsOrder) break;
			centerIndex = i;
			minAbsOrder = order;
		}
		
		this.x = this.originX - 4 - SLOT_SIZE * centerIndex;
		this.y = this.originY - 4;
	}
	
	// TODO: This is not the final logic of which slots should be present / visible.
	private void addSlotOrChildren(WearablesSlot slot)
	{
		if (slot.getChildren().isEmpty()) {
			if (slot.isEnabled()) this.slots.add(slot);
		} else slot.getChildren().forEach(this::addSlotOrChildren);
	}
	
	
	public int getX() { return this.x; }
	public int getY() { return this.y; }
	public int getWidth() { return 8 + SLOT_SIZE * this.slots.size(); }
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
	{
		x += screen.getLeft();
		y += screen.getTop();
		return ((pointX >= x) && (pointX < x + width)
		     && (pointY >= y) && (pointY < y + height));
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
		highlight = !Collections.disjoint(WearablesAPI.getValidSlots(cursorOrFocusedStack), this.slots);
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
			
			// for (int i = 0; i < this.slots.size(); i++)
			// 	drawSlot(this.x + 4 + i * SLOT_SIZE, this.y + 4);
		}
		
		if (highlight) {
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