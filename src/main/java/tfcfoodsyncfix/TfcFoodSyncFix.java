package tfcfoodsyncfix;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Client-side patch mod.
 *
 * <p>All patch logic lives in {@code tfcfoodsyncfix.mixin.DataManagerMixin}: when TFC's
 * {@code FoodCapability.MANAGER} finishes {@code DataManager#onSync} with data received from a
 * physical (dedicated) server, we rebuild {@code FoodCapability.CACHE} and invalidate all Forge
 * {@code Ingredient}s on the client main thread, so JEI can correctly enumerate
 * {@code tfc:not_rotten} and other FoodCapability-dependent ingredients.
 *
 * <p>This class deliberately contains no client-only imports, so it is safe to load on a
 * dedicated server where the mixin is not applied (see the "client" list in the mixin config).
 */
@Mod(TfcFoodSyncFix.MOD_ID)
public final class TfcFoodSyncFix
{
    public static final String MOD_ID = "tfc_food_sync_fix";
    public static final Logger LOGGER = LogUtils.getLogger();
}
