# Nue Zigbee 2 Gang GPO – Hubitat driver

Hubitat Elevation parent/child driver for the **Nue (3A Smart Home) Zigbee 2-gang power point**, model `LXN-2S27LX1.0`. Each outlet becomes its own child switch, and the parent tracks whether the device is online.

## Drivers

| Driver (Type name in Hubitat) | Version | Import URL |
|---|---|---|
| Parent Nue Zigbee 2 Gang GPO | v2.1 | [raw](https://raw.githubusercontent.com/mrfy8/hubitat-nue-zigbee-2-gang-gpo/main/Parent%20Nue%20Zigbee%202%20Gang%20GPO.groovy) |
| Nue Child GPO | v2.0 | [raw](https://raw.githubusercontent.com/mrfy8/hubitat-nue-zigbee-2-gang-gpo/main/Nue%20Child%20GPO.groovy) |

Install both: the parent creates its children with the child driver.

## Features

- **Two child outlets** – `Left GPO` (endpoint 1) and `Right GPO` (endpoint 2), each a standard Switch/Outlet you can use in any app or dashboard. Switch reports are routed to the right child by endpoint, so physical button presses stay in sync.
- **Parent switch** – follows a chosen outlet: **Left**, **Right**, **Either** (on if any outlet is on) or **Both** (on only if both are on). Turning the parent on or off switches the outlet(s) it follows. The parent also shows `leftSwitch` and `rightSwitch`.
- **Health check** – `healthStatus` (`online` / `offline` / `unknown`) and `lastCheckin`, on the parent and mirrored to both children. The device reports at least every 15 minutes; the driver pings it on a schedule and only marks it offline after a set number of pings go unanswered **and** a final confirming ping gets no reply within 20 seconds. That avoids false offline alerts from a single lost reply.
- **Commands** – `childOn` / `childOff` by outlet number (1 = Left, 2 = Right), `ping`, `identify` (blinks the device for 10 seconds), `refresh` and `resetToFactoryDefault`.

The device has no power metering, so the driver doesn't report power or energy.

## Installation

1. In Hubitat, go to **Drivers Code → New Driver → Import**, paste the parent's raw URL from the table above, then **Save**. Repeat for the child driver.
2. Pair the GPO (Devices → Add Device → Zigbee). Hubitat should pick the parent driver from its fingerprint; if not, open the device and set **Type** to `Parent Nue Zigbee 2 Gang GPO`.
3. On the device page, click **Configure**. This creates the `Left GPO` and `Right GPO` children and sets up reporting.

**After updating either driver, click Configure once.**

## Settings

| Setting | Default | Notes |
|---|---|---|
| Parent switch follows | Left | Left, Right, Either or Both |
| Health check interval (minutes) | 2 | 1–30 |
| Unanswered pings before offline | 2 | 1–10 |
| Info logging | On | |
| Debug logging | Off | Turns itself off after 30 minutes |

## Offline alerts

`healthStatus` makes a simple offline alert easy. For example, a Rule Machine rule triggered when `healthStatus` becomes `offline` **and stays that way for 2 minutes**, which then notifies your phone.

## Device fingerprint

| | |
|---|---|
| Model | `LXN-2S27LX1.0` |
| Manufacturer | `3A Smart Home DE` |
| Profile ID | `C05E` |
| In clusters | `0000,0004,0003,0006,0005,1000,0008` |

## Changelog

The full history is in the comment block at the top of each driver.

## Disclaimer

Community driver, not affiliated with Nue or 3A Smart Home. Use at your own risk.
