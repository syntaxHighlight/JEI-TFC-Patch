package tfcfoodsyncfix.mixin;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.util.DataManager;
import tfcfoodsyncfix.ClientFoodSyncFix;

/**
 * Patches {@code net.dries007.tfc.util.DataManager#onSync}, which is the exact point where TFC
 * replaces a client-side data manager with data synced from a physical (dedicated) server.
 *
 * <p>Vanilla TFC rebuilds derived {@link net.dries007.tfc.util.collections.IndirectHashCollection}
 * caches on {@code TagsUpdatedEvent}, but {@code onSync} only updates the manager's own map. For
 * {@code FoodCapability.MANAGER} this leaves {@code FoodCapability.CACHE} stale, so JEI's
 * first-time recipe indexing (on a client that joins before/around the food sync) enumerates
 * {@code tfc:not_rotten} ingredients as empty and caches that result.
 *
 * <p>We only patch the food manager ({@code (Object) this == FoodCapability.MANAGER}); all other
 * managers (heat, metal, fuel, ...) are left untouched. The memory-connection (single player)
 * branch of {@code onSync} is excluded explicitly, preserving TFC's vanilla single-player
 * behavior.
 */
// remap = false: the target is a TFC (mod) class, whose names are identical in the dev and
// production environments, so no obfuscation mapping applies.
@Mixin(value = DataManager.class, remap = false)
public abstract class DataManagerMixin
{
    @Inject(method = "onSync", at = @At("TAIL"), remap = false)
    private void tfcfoodsyncfix$afterFoodManagerSync(NetworkEvent.Context context, Map<ResourceLocation, ?> elements, CallbackInfo ci)
    {
        if ((Object) this == FoodCapability.MANAGER && !context.getNetworkManager().isMemoryConnection())
        {
            // Sync received from a physical server and fully applied. Rebuild the derived food
            // cache and invalidate all ingredients (on the client main thread) so JEI indexes
            // the correct item stacks.
            ClientFoodSyncFix.afterFoodManagerSync(elements.size());
        }
    }
}
