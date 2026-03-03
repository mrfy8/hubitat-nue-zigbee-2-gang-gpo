# Nue Zigbee 2-Gang GPO (Power Monitoring) - Hubitat Driver
**Namespace:** mr_fy_hubitat  
**Model Supported:** LXN-2S27LX1.0 (3A Smart Home / Nue)

This driver provides full support for the Nue Zigbee Double Power Outlet (GPO). It includes individual control of both outlets through a Parent/Child device structure.

## Features
- **Dual Outlet Control:** Automatically creates two child devices ("Left GPO" and "Right GPO") for independent control.
- **Auto-Logging Cleanup:** Debug logging automatically disables after 30 minutes to keep your hub logs clean.

## Installation
1. Add the **Nue Child GPO** code to the 'Drivers Code' section of your Hubitat hub.
2. Add the **Parent Nue Zigbee 2 Gang GPO** code to 'Drivers Code'.
3. Pair your Nue Double GPO with Hubitat.
4. Once paired, select the **Parent Nue Zigbee 2 Gang GPO** driver for the device.
5. Click **Save Device**.
6. On the device page, click **Initialize** and then **Configure**. This will automatically generate the two child outlet devices.

---
*Developed by mr_fy for the Hubitat community.*
