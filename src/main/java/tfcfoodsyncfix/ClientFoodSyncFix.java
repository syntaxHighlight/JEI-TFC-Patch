package tfcfoodsyncfix;

import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.FoodDefinition;

/**
 * Client-only fix logic. Loaded lazily, only from the client-side mixin, so it is never
 * classloaded on a dedicated server.
 */
public final class ClientFoodSyncFix
{
    private ClientFoodSyncFix() {}

    /**
     * Called right after TFC's {@code FoodCapability.MANAGER} has been replaced by data synced
     * from a physical (dedicated) server, i.e. after {@code DataManager#onSync} has completed
     * {@code types.clear(); types.putAll(elements); updateReferences();}.
     *
     * @param syncedDefinitionCount the number of food definitions received in the sync packet
     */
    public static void afterFoodManagerSync(final int syncedDefinitionCount)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread())
        {
            reload(syncedDefinitionCount);
        }
        else
        {
            // The MANAGER update itself is already complete (onSync ran to the tail before this
            // was called), so deferring only the derived cache rebuild + ingredient invalidation
            // to the main thread keeps everything consistent and race-free.
            minecraft.execute(() -> reload(syncedDefinitionCount));
        }
    }

    private static void reload(final int syncedDefinitionCount)
    {
        final int definitionCount = FoodCapability.MANAGER.getValues().size();

        // 1) Invalidate ingredients FIRST: bumps Forge's global ingredient invalidation counter,
        //    so every Ingredient's lazy itemStacks cache is dropped on next access. This matters
        //    because the cache rebuild below derives items via FoodDefinition#getValidItems() ->
        //    Ingredient#getItems(); invalidating first guarantees that enumeration is re-derived
        //    from the current state (and, for tag-based ingredients, the current tag data)
        //    instead of a stale cached enumeration.
        Ingredient.invalidateAll();

        // 2) Rebuild the derived cache from the just-synced definitions.
        FoodCapability.CACHE.reload(FoodCapability.MANAGER.getValues());

        // 3) Invalidate AGAIN: consumers that already cached item stacks derived from the stale
        //    state (TFC's DelegateIngredient / NotRottenIngredient, and anything else that
        //    enumerated before the sync) re-enumerate lazily on next use, now observing the
        //    freshly rebuilt CACHE. This is what lets JEI's recipe indexing see correct data.
        Ingredient.invalidateAll();

        // 4) Refresh the player's inventory stacks. Item capabilities (food, heat, ...) are
        //    attached exactly once, when an ItemStack instance is created, based on the
        //    definition caches at that moment. Vanilla syncs the player's inventory during
        //    login BEFORE TFC's data manager sync packets arrive, so those stack instances were
        //    created while the caches were still empty and permanently lack their capabilities
        //    (they then fail e.g. tfc:not_rotten ingredient matching - sandwiches, salads,
        //    soups, or any capability-driven behavior on those exact instances). stack#copy()
        //    creates fresh instances, re-running AttachCapabilitiesEvent now that the caches
        //    are populated. NBT (decay dates, traits, temperatures) is preserved by copy.
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player != null)
        {
            refreshLocalPlayerInventory(player);

            // Curios slots are synced by their own OnDatapackSyncEvent listener, whose ordering
            // relative to TFC's sync listeners is not guaranteed; refresh them the same way.
            // Only touched when curios is actually installed (soft dependency).
            if (ModList.get().isLoaded("curios"))
            {
                CuriosSlotRefresher.refresh(player);
            }
        }

        TfcFoodSyncFix.LOGGER.info("[TFC Food Sync Fix] Physical-server food sync processed: {} food definition(s), food cache rebuilt, ingredients invalidated", definitionCount);

        if (definitionCount != syncedDefinitionCount)
        {
            TfcFoodSyncFix.LOGGER.warn("[TFC Food Sync Fix] Definition count mismatch between sync packet ({}) and manager ({})", syncedDefinitionCount, definitionCount);
        }

        if (TfcFoodSyncFix.LOGGER.isDebugEnabled())
        {
            final Item melon = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("tfc:melon"));
            if (melon != null && melon != Items.AIR)
            {
                final FoodDefinition definition = FoodCapability.getDefinition(new ItemStack(melon));
                TfcFoodSyncFix.LOGGER.debug("[TFC Food Sync Fix] tfc:melon food definition after fix: {}", Objects.isNull(definition) ? "null" : "found");
            }
        }
    }

    /**
     * Re-creates every item stack instance in the local player's inventory (main inventory,
     * armor and offhand slots) so that item capabilities are re-attached against the now
     * rebuilt definition caches.
     */
    private static void refreshLocalPlayerInventory(LocalPlayer player)
    {
        final Inventory inventory = player.getInventory();
        int refreshed = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++)
        {
            final ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty())
            {
                inventory.setItem(i, stack.copy());
                refreshed++;
            }
        }

        if (refreshed > 0 && TfcFoodSyncFix.LOGGER.isDebugEnabled())
        {
            TfcFoodSyncFix.LOGGER.debug("[TFC Food Sync Fix] Re-created {} player inventory item stack(s) to re-attach item capabilities after cache rebuild", refreshed);
        }
    }
}
