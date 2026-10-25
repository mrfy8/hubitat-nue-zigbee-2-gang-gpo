/*
 *  Nue Child GPO
 *  Child outlet for "Parent Nue Zigbee 2 Gang GPO"
 *
 *  2025-07-08  v1.0  Initial
 *  2026-09-17  v2.0  - Removed metering (device has no metering clusters)
 *                    - Added healthStatus / lastCheckin (mirrored from parent)
 *                    - Debug logging follows the parent's setting
 */

metadata {
    definition(name: "Nue Child GPO", namespace: "mr_fy_hubitat", author: "mr_fy") {
        capability "Actuator"
        capability "Switch"
        capability "Outlet"
        capability "Refresh"

        attribute "healthStatus", "enum", ["online", "offline", "unknown"]
        attribute "lastCheckin", "string"
    }
}

void installed() {
    logDebug "Installed"
}

void updated() {
    logDebug "Updated"
}

// Children receive no raw Zigbee traffic; the parent forwards events.
void parse(String description) {
    logDebug "Unexpected raw parse: ${description}"
}

void parse(List events) {
    events?.each { Map evt ->
        logDebug "Event: ${evt}"
        sendEvent(evt)
    }
}

void on() {
    parent?.componentOn(device)
}

void off() {
    parent?.componentOff(device)
}

void refresh() {
    parent?.componentRefresh(device)
}

// Called by parent configure() to clear v1.0 metering states
void cleanupStates() {
    ["power", "voltage", "current", "amperage", "energy"].each { String a ->
        try { device.deleteCurrentState(a) } catch (Exception ignored) { }
    }
}

private void logDebug(String msg) {
    Boolean dbg = false
    try { dbg = parent?.debugEnabled() } catch (Exception ignored) { }
    if (dbg) log.debug "${device.displayName}: ${msg}"
}
