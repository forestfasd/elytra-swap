package dev.elytraswap;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.lwjgl.glfw.GLFW;

/**
 * One key: put the best elytra from your inventory into your chest slot.
 * Whatever was in the chest slot goes to where the elytra was.
 * It never takes an elytra off - it only ever puts one on.
 */
public class ElytraSwapClient implements ClientModInitializer {

	public static final String MOD_ID = "elytraswap";

	// Slot numbers inside the player's own inventory menu (containerId 0).
	private static final int MENU_CHEST_SLOT = 6;

	// Inventory index of the offhand. Also the SWAP button value for it
	// (SWAP buttons 0-8 = hotbar slots).
	private static final int OFFHAND_INV_INDEX = 40;

	private static KeyMapping swapKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		swapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.elytraswap.swap",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_GRAVE_ACCENT, // change it in Options > Controls > Key Binds
				category));

		ClientTickEvents.END_CLIENT_TICK.register(ElytraSwapClient::onTick);
	}

	private static void onTick(Minecraft mc) {
		// Cheap check first: nothing to do unless the key was pressed.
		if (!swapKey.consumeClick()) return;
		// Drain extra presses queued this tick so one tick = at most one swap.
		while (swapKey.consumeClick()) { }

		equipElytra(mc);
	}

	private static void equipElytra(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null) return;
		if (player.isDeadOrDying() || player.isSpectator()) return;
		// Only act on the player's own inventory (no chest/furnace/etc. open).
		if (player.containerMenu != player.inventoryMenu) return;

		ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);

		// Curse of Binding: the server won't let it come off, so don't try.
		if (!worn.isEmpty() && !player.isCreative()
				&& EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
			return;
		}

		int invIndex = findBestElytra(player.getInventory());
		if (invIndex < 0) return;

		// Already wearing a working elytra? Leave it alone.
		// (If the worn one is about to break, swap in a fresh one.)
		if (isElytra(worn) && !worn.nextDamageWillBreak()) return;

		int containerId = player.inventoryMenu.containerId;

		if (invIndex < 9 || invIndex == OFFHAND_INV_INDEX) {
			// Hotbar or offhand: a single SWAP click does it (one packet).
			mc.gameMode.handleContainerInput(containerId, MENU_CHEST_SLOT, invIndex,
					ContainerInput.SWAP, player);
		} else {
			// Main inventory (inventory index 9-35 == menu slot 9-35):
			// pick up elytra, drop it on the chest slot, put the old item back.
			mc.gameMode.handleContainerInput(containerId, invIndex, 0, ContainerInput.PICKUP, player);
			mc.gameMode.handleContainerInput(containerId, MENU_CHEST_SLOT, 0, ContainerInput.PICKUP, player);
			mc.gameMode.handleContainerInput(containerId, invIndex, 0, ContainerInput.PICKUP, player);
		}
	}

	/** Usable elytra with the most durability left, or -1 if none. */
	private static int findBestElytra(Inventory inv) {
		int best = -1;
		int bestLeft = -1;

		for (int i = 0; i < 36; i++) {
			int left = usableDurability(inv.getItem(i));
			if (left > bestLeft) {
				bestLeft = left;
				best = i;
			}
		}
		int off = usableDurability(inv.getItem(OFFHAND_INV_INDEX));
		if (off > bestLeft) best = OFFHAND_INV_INDEX;

		return best;
	}

	/** Remaining durability of a working elytra, or -1 if it isn't one / is broken. */
	private static int usableDurability(ItemStack stack) {
		if (!isElytra(stack) || stack.nextDamageWillBreak()) return -1;
		if (!stack.isDamageableItem()) return Integer.MAX_VALUE; // unbreakable
		return stack.getMaxDamage() - stack.getDamageValue();
	}

	/** Vanilla elytra and any modded item that can glide. */
	private static boolean isElytra(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.GLIDER);
	}
}
