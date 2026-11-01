# oneplus-oxygenos-esimpatch-lsp

**English** | [简体中文](README-CN.md)

An LSPosed (Vector) module that wires the "half-broken" eSIM entries back to something actually usable after flashing a **OnePlus 13T with OxygenOS 13s firmware**.

> **Recommended companion to [`kinginu/oneplus13t-esim`](https://github.com/kinginu/oneplus13t-esim)**: that Magisk module makes the eSIM entry show up in Settings; this module then redirects that entry's action to something that works.

## Background

After flashing OxygenOS 13s on a OnePlus 13T (CN model):

- The internal eUICC (NXP SN220U) is **dead** — the hardware is there, but the CN board was never personalized; the modem reports `CARDSTATE_ABSENT`. Not fixable at the AP layer.
- The Google LPA software stack is **complete**, but the "add eSIM profile" step fails with `0x20009` (the framework does not publish the removable card's EID to the public euicc API).
- The Settings eSIM page is basically empty, and the standalone entry is hidden by OnePlus.

So the path that actually works: **insert a removable eUICC card (5ber / eSTK.me / 9eSIM, etc.) and download with a third-party LPA such as EasyEUICC / NekokoLPA**. This module redirects the broken Settings entries to those working tools.

## Features

### Hook 1: Settings entry → Google LPA

When `EsimSettingsActivity` in `com.android.phone` (the "SIM & eSIM settings" item in Settings) launches, it jumps straight to the native Google LPA manager page (`MANAGE_EMBEDDED_SUBSCRIPTIONS` → `CurrentProfileListActivity`), showing the profiles on the card.

### Hook 2: Add SIM → EasyEUICC

When `ProfileListFragment.mButtonAddProfile` in `com.google.android.euicc` (the "Add SIM" button in Google LPA) is clicked, it jumps to **EasyEUICC's `DownloadWizardActivity`**, going directly into the eSIM download wizard.

> Why not NekokoLPA: NekokoLPA is a single-Activity Flutter app whose UI and routing live in the Dart layer, so an external Intent cannot target its "add" sub-page — it can only land on the main screen. EasyEUICC has a native `DownloadWizardActivity` that goes straight into the download flow.

## Scope

**Device**: OnePlus 13T (`PKX110` / `CPH2723`) running OxygenOS 13s firmware, rooted (Magisk / KernelSU) with LSPosed/Vector installed.

**Prerequisites**:

1. [`kinginu/oneplus13t-esim`](https://github.com/kinginu/oneplus13t-esim) installed (makes the eSIM entry appear in Settings);
2. **EasyEUICC** (`im.angry.easyeuicc.ce`) installed — Hook 2 depends on it;
3. A removable eUICC card inserted (5ber / eSTK.me / 9eSIM) with its **ATR Mode switched to eUICC**;
4. Do not touch OPPO's eSIM master switch (it belongs to the dead internal eUICC; enabling it only breaks SIM2).

**Not applicable to**: the real 13S (which has a working internal eUICC), non-OxygenOS systems (ColorOS needs a different approach), or unrooted devices.

## Installation

1. Install `dist/EsimPatch-v1.2.apk`;
2. Open LSPosed/Vector and enable EsimPatch;
3. **Check both scope apps**: `com.android.phone` and `com.google.android.euicc`;
4. Reboot (or at least kill both processes).

## Verification

- Settings → Mobile network → SIM & eSIM settings: should land directly on Google LPA's Carrier page (profile list + Add SIM + Reset carriers);
- Tap **Add SIM**: should enter EasyEUICC's "Select or confirm the eSIM..." download wizard.

## Known limitations

- **Downloads still hit the `0x20009` limit**: this module only redirects entries to EasyEUICC; Google LPA's own download flow (when not going through this module) still gets stuck.
- **Class/field names depend on the current firmware**: `EsimSettingsActivity`, `ProfileListFragment`, and `mButtonAddProfile` are symbols from the current OxygenOS 13s build. They may break after an OTA update — the module log (tag `EsimPatch`) will print `class not found` / `hooked Google LPA failed`, and the hooks need re-adapting.
- **Popups**: the "Keep this eUICC available" prompt NekokoLPA shows on every launch is its own Dart-layer dialog and unrelated to this module (this module uses EasyEUICC instead, so it never triggers it).

## Build

Requires Android SDK build-tools + an `android.jar`:

```bash
./build.sh
```

The output lands in `dist/EsimPatch-v1.2.apk`.

## License

MIT
