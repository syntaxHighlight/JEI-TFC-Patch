# TFC Food Sync Fix

A client-side patch mod for Minecraft 1.20.1 (Forge) + TerraFirmaCraft + JEI.

## Features

- Fixes `tfc:not_rotten` (and other FoodCapability-dependent) ingredients missing from JEI's usage index (U) when the client connects to a dedicated server.
- Rebuilds the TFC food definition cache (`FoodCapability.CACHE`) and invalidates Forge ingredients right after TFC's food data sync from a physical server completes, on the client main thread.
- Re-creates the local player's inventory and curios item stacks after the sync: those instances were created during login (before TFC's data sync arrived) while the definition caches were still empty, so their item capabilities (food, heat, ...) never attached — which broke `tfc:not_rotten` ingredient matching (sandwiches, salads, soups) for items obtained at login. (v1.1.0)
- Only touches the Food manager's sync path — no other TFC managers (heat, metal, fuel, etc.) are affected.
- Client-side only. Does not touch JEI's API, does not trigger datapack / resource reloads, and does not alter recipe matching behavior.
- Safe to run without JEI installed. Curios support is optional (soft dependency).

## Known limitation — requires a modpack-side change for a full fix

This mod does **not** fully fix sandwich-tag issues on its own.

Foods whose eligibility for sandwiches (or other tag-based TFC recipes) is granted via
CraftTweaker scripts — e.g. `scripts/food_tfc.zs` doing
`<tag:items:tfc:foods/usable_in_sandwich>.add(<item:tfc:food/lemon>, ...)` — can still fail:

- TFC's sandwich recipe ingredient wraps the tag in a `tfc:not_rotten` ingredient. When the
  server syncs recipes to the client, tag-based ingredients are expanded into concrete item
  lists through `Ingredient#toNetwork` → `getItems()`, which is a lazily cached value.
- If that cache is built before the CraftTweaker tag edits apply (or is simply never
  invalidated afterwards — Forge does not invalidate ingredients on tag updates), the synced
  recipe permanently lacks the script-added foods (e.g. lemon). No client-side mod can fix
  that, because the recipe data itself is missing on the wire.

For a complete fix, migrate CraftTweaker tag edits to datapack tags, e.g.
`kubejs/data/tfc/tags/items/foods/usable_in_sandwich.json`:

```json
{ "replace": false, "values": ["tfc:food/lemon", "tfc:food/orange"] }
```

(Alternatively, append `Ingredient.invalidateAll();` at the end of the scripts so the server
re-expands ingredient caches after the tag edits — datapack tags are the cleaner option.)

## AI-assisted development

This mod was written using AI-assisted "vibe coding": the fix was specified, implemented, and refined through iterative collaboration with an AI coding agent, then reviewed and tested by hand.
