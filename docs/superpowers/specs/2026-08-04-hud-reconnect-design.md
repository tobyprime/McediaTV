# HUD Reconnect Playback Design

## Goal

Fix the 1.21.11 client so a persistent HUD channel binding continues playing after reconnecting without rebinding it.

## Root Cause

The server already sends the persistent `hud_binding` on player join. The client creates a channel session when that payload arrives, but `MtvClientChannelPayloads` clears all channel sessions again from the client `JOIN` callback. `HudChannelPlayer.currentChannelId` is not cleared by that manager operation, so a later identical binding is treated as a duplicate and ignored. Rebinding works only because it changes the channel transition.

## Design

- Treat `DISCONNECT` as the only connection transition that destroys runtime channel sessions.
- Do not clear channel sessions from `JOIN`; this also prevents an early join payload from being destroyed by the join callback.
- Centralize disconnect cleanup so HUD peripherals detach before channel hosts are destroyed.
- Keep the existing network protocol and server behavior unchanged.
- Add lifecycle logs at payload receipt, connection transitions, HUD subscribe/unsubscribe, channel session attach/detach/clear, snapshot routing, and packet send rejection.

## Compatibility

This is client-only and does not change packet IDs or payload formats. The 1.21.11 client remains compatible with existing servers and unchanged servers remain compatible with existing clients.

## Verification

- Add a focused lifecycle regression test proving `JOIN` preserves sessions and `DISCONNECT` clears them in HUD-before-session order.
- Run the focused 1.21.11 test task and build task.
- Inspect the final diff to verify no 26.1/26.2 source files changed.
