package tfcfoodsyncfix;

import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
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

        // Rebuild the derived cache BEFORE invalidating ingredients, so the next ingredient
        // enumeration (i.e. JEI building its recipe index, or NotRottenIngredient#getItems)
        // observes a consistent, up-to-date view.
        FoodCapability.CACHE.reload(FoodCapability.MANAGER.getValues());

        // Force TFC's DelegateIngredient / NotRottenIngredient etc. to drop their cached
        // ItemStack[] and re-enumerate on next use, so JEI's reverse lookup index is rebuilt
        // from correct data.
        Ingredient.invalidateAll();

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
}
