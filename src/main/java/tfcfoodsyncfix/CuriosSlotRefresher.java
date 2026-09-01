package tfcfoodsyncfix;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Re-creates curios slot item stacks so their capabilities re-attach against the rebuilt
 * definition caches.
 *
 * <p>Curios syncs its slots on {@code OnDatapackSyncEvent}, the same event TFC uses to send its
 * data manager syncs. The relative ordering of the two listeners is not guaranteed: if curios'
 * sync arrives before TFC's food sync, curios slot instances were created while the definition
 * caches were still empty and permanently lack their capabilities.
 *
 * <p>Safety: the copy is NBT-identical to the original, so {@code ItemStack.matches} between
 * the previous and new stack stays true - Curios will not treat this as a slot change, so no
 * extra sync packets are sent and attribute modifiers are not re-evaluated.
 *
 * <p>This class is only referenced behind a {@code ModList.isLoaded("curios")} guard and is
 * never classloaded when curios is absent.
 */
public final class CuriosSlotRefresher
{
    private CuriosSlotRefresher() {}

    public static void refresh(LocalPlayer player)
    {
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
        {
            int refreshed = 0;
            for (ICurioStacksHandler stacksHandler : handler.getCurios().values())
            {
                refreshed += copyStacks(stacksHandler.getStacks());
                refreshed += copyStacks(stacksHandler.getCosmeticStacks());
            }

            if (refreshed > 0 && TfcFoodSyncFix.LOGGER.isDebugEnabled())
            {
                TfcFoodSyncFix.LOGGER.debug("[TFC Food Sync Fix] Re-created {} curios item stack(s) to re-attach item capabilities after cache rebuild", refreshed);
            }
        });
    }

    private static int copyStacks(IDynamicStackHandler stacks)
    {
        int count = 0;
        for (int i = 0; i < stacks.getSlots(); i++)
        {
            final ItemStack stack = stacks.getStackInSlot(i);
            if (!stack.isEmpty())
            {
                stacks.setStackInSlot(i, stack.copy());
                count++;
            }
        }
        return count;
    }
}
