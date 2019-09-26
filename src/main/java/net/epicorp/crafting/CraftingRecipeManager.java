package net.epicorp.crafting;

import net.epicorp.utilities.inventories.Inventories;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CraftingRecipeManager implements Listener {
	private Plugin plugin;
	private List<CraftingRecipe> recipes = new ArrayList<>();

	public CraftingRecipeManager(Plugin plugin) {
		this.plugin = plugin;
	}

	public void register(CraftingRecipe recipe) {
		this.recipes.add(recipe);
	}

	public List<CraftingRecipe> getRecipes() {
		return recipes;
	}

	public void setRecipes(List<CraftingRecipe> recipes) {
		this.recipes = recipes;
	}

	private Set<UUID> players = Collections.newSetFromMap(new ConcurrentHashMap<>());

	@EventHandler
	public void craftEvent(InventoryClickEvent event) { // here, we are setting the item in the user's hand
		Inventory inv = event.getClickedInventory();
		if (inv instanceof CraftingInventory) { // if the user clicked the output slot of the inventory
			if (event.getSlot() == 0) {
				boolean craftAll = false;
				if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) craftAll = true;
				final boolean craft = craftAll;
				ItemStack[] matrix = Inventories.clone(((CraftingInventory) inv).getMatrix());
				Inventories.clean(matrix);
				recipes.stream().map(c -> c.output(matrix, craft)).filter(Objects::nonNull).findFirst().ifPresent(i -> {
					if (craft && !Inventories.canAddStack(i, event.getView().getBottomInventory())) // if shiftclick and inventory is full
						return;
					UUID uuid = event.getView().getPlayer().getUniqueId();
					players.add(uuid);
					event.setCurrentItem(i);
					Bukkit.getScheduler().runTaskLater(plugin, () -> {
						try {
							Inventories.clean(matrix);
							((CraftingInventory) inv).setMatrix(matrix);
							display((CraftingInventory) inv);
							((Player) event.getWhoClicked()).updateInventory();
						} finally {
							players.remove(uuid);
						}
					}, 0);
				});
			}
		}
	}

	@EventHandler
	public void prep(PrepareItemCraftEvent event) { // only thing that happens here, is that we are DISPLAYING the result item
		if (!players.contains(event.getView().getPlayer().getUniqueId()))
			display(event.getInventory());
	}

	private void display(CraftingInventory inventory) {
		ItemStack[] matrix = Inventories.clone(inventory.getMatrix());
		Inventories.clean(matrix);
		recipes.stream().map(c -> c.output(matrix, false)).filter(Objects::nonNull).findFirst().ifPresent(inventory::setResult);
	}

	@EventHandler
	public void log(PlayerQuitEvent event) {
		players.remove(event.getPlayer().getUniqueId()); // just in case lock set does not update properly, this will allow the player to relog to fix the issue
	}
}
