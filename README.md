# TFC Food Sync Fix

A client-side patch mod for Minecraft 1.20.1 (Forge) + TerraFirmaCraft + JEI.

## Features

- Fixes `tfc:not_rotten` (and other FoodCapability-dependent) ingredients missing from JEI's usage index (U) when the client connects to a dedicated server.
- Rebuilds the TFC food definition cache (`FoodCapability.CACHE`) and invalidates Forge ingredients right after TFC's food data sync from a physical server completes, on the client main thread.
- Only touches the Food manager's sync path — no other TFC managers (heat, metal, fuel, etc.) are affected.
- Client-side only. Does not touch JEI's API, does not trigger datapack / resource reloads, and does not alter recipe matching behavior.
- Safe to run without JEI installed.

## AI-assisted development

This mod was written using AI-assisted "vibe coding": the fix was specified, implemented, and refined through iterative collaboration with an AI coding agent, then reviewed and tested by hand.
