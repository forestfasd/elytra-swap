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
import net.minecraft.world.item.equipment.Equippable;

/**
 * One key toggles your chest slot between an elytra and a chestplate.
 * Whatever was in the chest slot goes to where the new item came from.
 */
public class ElytraSwapClient implements ClientModInitializer {

	public static final String MOD_ID = "elytraswap";

	// Slot numbers inside the player's own inventory menu (containerId 0).
	private static final int MENU_CHEST_SLOT = 6;

	// Inventory index of the offhand. Also the SWAP button value for it
	// (SWAP buttons 0-8 = hotbar slots).
	private static final int OFFHAND_INV_INDEX = 40;

	// 26.3 uses SDL scancodes for keys; 53 = the ` (grave accent) key.
	private static final int KEY_GRAVE_ACCENT = 53;

	private static KeyMapping swapKey;

	// Where the chestplate went when we last put an elytra on (-1 = unknown).
	private static int lastChestplateIndex = -1;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		swapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.elytraswap.swap",
				InputConstants.Type.KEYBOARD,
				KEY_GRAVE_ACCENT, // change it in Options > Controls > Key Binds
				category));

		ClientTickEvents.END_CLIENT_TICK.register(ElytraSwapClient::onTick);
	}

	private static void onTick(Minecraft mc) {
		// Cheap check first: nothing to do unless the key was pressed.
		if (!swapKey.consumeClick()) return;
		// Drain extra presses queued this tick so one tick = at most one swap.
		while (swapKey.consumeClick()) { }

		toggle(mc);
	}

	/**
	 * Wearing an elytra -> put a chestplate back on.
	 * Anything else (chestplate, other item, nothing) -> put the best elytra on.
	 */
	private static void toggle(Minecraft mc) {
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

		Inventory inv = player.getInventory();

		if (isElytra(worn)) {
			int chest = findChestplate(inv);
			if (chest >= 0) swapIntoChest(mc, player, chest);
		} else {
			int elytra = findBestElytra(inv);
			if (elytra >= 0) {
				swapIntoChest(mc, player, elytra);
				// The old chest item now sits where the elytra was.
				lastChestplateIndex = elytra;
			}
		}
	}

	/** Swap the item at this inventory index with the chest slot. */
	private static void swapIntoChest(Minecraft mc, LocalPlayer player, int invIndex) {
		int containerId = player.inventoryMenu.containerId;

		if (invIndex < 9 || invIndex == OFFHAND_INV_INDEX) {
			// Hotbar or offhand: a single SWAP click does it (one packet).
			mc.gameMode.handleContainerInput(containerId, MENU_CHEST_SLOT, invIndex,
					ContainerInput.SWAP, player);
		} else {
			// Main inventory (inventory index 9-35 == menu slot 9-35):
			// pick it up, drop it on the chest slot, put the old item back.
			mc.gameMode.handleContainerInput(containerId, invIndex, 0, ContainerInput.PICKUP, player);
			mc.gameMode.handleContainerInput(containerId, MENU_CHEST_SLOT, 0, ContainerInput.PICKUP, player);
			mc.gameMode.handleContainerInput(containerId, invIndex, 0, ContainerInput.PICKUP, player);
		}
	}

	/**
	 * The chestplate to put back on: the one this mod took off last time if it's
	 * still in the same slot, otherwise the first chestplate found
	 * (main inventory, then offhand, then hotbar).
	 */
	private static int findChestplate(Inventory inv) {
		if (lastChestplateIndex >= 0 && isChestplate(inv.getItem(lastChestplateIndex))) {
			return lastChestplateIndex;
		}
		for (int i = 9; i < 36; i++) {
			if (isChestplate(inv.getItem(i))) return i;
		}
		if (isChestplate(inv.getItem(OFFHAND_INV_INDEX))) return OFFHAND_INV_INDEX;
		for (int i = 0; i < 9; i++) {
			if (isChestplate(inv.getItem(i))) return i;
		}
		return -1;
	}

	/** Anything worn in the chest slot that isn't a glider. */
	private static boolean isChestplate(ItemStack stack) {
		if (stack.isEmpty() || isElytra(stack)) return false;
		Equippable eq = stack.get(DataComponents.EQUIPPABLE);
		return eq != null && eq.slot() == EquipmentSlot.CHEST;
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
