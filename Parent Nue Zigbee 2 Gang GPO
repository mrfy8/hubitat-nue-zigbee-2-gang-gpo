/*
 *  Parent Nue Zigbee 2 Gang GPO
 *  3A Smart Home DE  LXN-2S27LX1.0  (on/off only - no metering clusters)
 *
 *  Endpoint 1 = Left outlet, Endpoint 2 = Right outlet
 *
 *  2025-07-08  v1.0  Initial (unfinished)
 *  2026-09-17  v2.0  - Removed metering (device has no 0B04/0702 clusters)
 *                    - Parent switch follows a selectable outlet (Left/Right/Either/Both)
 *                    - leftSwitch / rightSwitch attributes on parent
 *                    - Switch reports routed to the correct child by endpoint
 *                    - healthStatus / lastCheckin with scheduled ping
 *                    - Fixed on/off command format, refresh, identify, factory reset
 *                    - Tidied logging
 *  2026-09-26  v2.1  - Health check counts unanswered pings, not silent intervals.
 *                      A quiet interval is normal (the device only reports every
 *                      15 min), so it now just triggers a ping; only a ping that
 *                      gets no reply counts. v2.0 went offline after one lost reply.
 *                    - Offline is declared only after a final confirming ping goes
 *                      unanswered for 20 s, so a device that answers is never
 *                      flipped offline and straight back online.
 *                    - "Silent checks before offline" is now "Unanswered pings
 *                      before offline" (1-10, default 2); saved value is kept.
 *
 *  After installing/updating: click Configure once.
 */

import groovy.transform.Field

@Field static final Integer REPORT_MAX_SECS = 900   // on/off report at least every 15 min
@Field static final Integer CONFIRM_SECS = 20       // wait for the confirming ping before offline

metadata {
    definition(name: "Parent Nue Zigbee 2 Gang GPO", namespace: "mr_fy_hubitat", author: "mr_fy") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        capability "Outlet"

        attribute "leftSwitch", "enum", ["on", "off"]
        attribute "rightSwitch", "enum", ["on", "off"]
        attribute "healthStatus", "enum", ["online", "offline", "unknown"]
        attribute "lastCheckin", "string"

        command "childOn", [[name: "Outlet number*", type: "NUMBER", description: "1 = Left, 2 = Right"]]
        command "childOff", [[name: "Outlet number*", type: "NUMBER", description: "1 = Left, 2 = Right"]]
        command "ping"
        command "identify"
        command "resetToFactoryDefault"

        fingerprint profileId: "C05E", endpointId: "01", inClusters: "0000,0004,0003,0006,0005,1000,0008", outClusters: "0000", model: "LXN-2S27LX1.0", manufacturer: "3A Smart Home DE", controllerType: "ZGB"
    }

    preferences {
        input name: "parentFollows", type: "enum", title: "Parent switch follows",
              options: [["left": "Left outlet"], ["right": "Right outlet"],
                        ["either": "Either (on if any outlet is on)"], ["both": "Both (on only if both are on)"]],
              defaultValue: "left", required: true
        input name: "healthInterval", type: "number", title: "Health check interval (minutes, 1-30)",
              defaultValue: 2, range: "1..30"
        input name: "healthMissed", type: "number", title: "Unanswered pings before offline (1-10)",
              defaultValue: 2, range: "1..10"
        input name: "enableInfo", type: "bool", title: "Enable info logging", defaultValue: true
        input name: "enableDebug", type: "bool", title: "Enable debug logging (auto-off after 30 min)", defaultValue: false
    }
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

void installed() {
    logInfo "Installed"
    sendEvent(name: "healthStatus", value: "unknown")
    configure()
}

void updated() {
    logInfo "Preferences saved"
    unschedule("logsOff")
    if (settings.enableDebug) {
        log.warn "${device.displayName}: debug logging enabled for 30 minutes"
        runIn(1800, "logsOff")
    }
    scheduleHealthCheck()
    recomputeParentSwitch()
}

void configure() {
    logInfo "Configuring"
    unschedule()

    // Clean up leftovers from v1.0
    state.remove("lastSwitch")
    ["power", "voltage", "amperage", "energy"].each { String a ->
        try { device.deleteCurrentState(a) } catch (Exception ignored) { }
    }

    // Fresh health-check state
    state.missed = 0
    state.pingPending = false
    state.remove("confirmFrom")

    createChildDevices()
    getChildDevices().each { cd ->
        try { cd.cleanupStates() } catch (Exception ignored) { }
    }

    if (!device.currentValue("healthStatus")) sendEvent(name: "healthStatus", value: "unknown")
    pushToChildren([name: "healthStatus", value: device.currentValue("healthStatus") ?: "unknown"])

    List cmds = []
    [1, 2].each { Integer ep ->
        String e = hex2(ep)
        cmds << "zdo bind 0x${dni()} 0x${e} 0x01 0x0006 {${device.zigbeeId}} {}"
        cmds << "delay 500"
        cmds << "he cr 0x${dni()} 0x${e} 0x0006 0x0000 0x10 0 ${REPORT_MAX_SECS} {}"
        cmds << "delay 500"
    }
    cmds += readOnOffCmds([1, 2])
    sendZigbee(cmds)

    scheduleHealthCheck()
    if (settings.enableDebug) runIn(1800, "logsOff")
}

void logsOff() {
    device.updateSetting("enableDebug", [value: "false", type: "bool"])
    log.warn "${device.displayName}: debug logging disabled"
}

// ---------------------------------------------------------------------------
// Parsing
// ---------------------------------------------------------------------------

void parse(String description) {
    logDebug "parse: ${description}"
    markAlive()

    Map msg = zigbee.parseDescriptionAsMap(description)
    if (!msg) return

    String cluster = ((msg.cluster ?: msg.clusterId) ?: "").toString().toUpperCase()
    Integer ep = hexToInt(msg.endpoint ?: msg.sourceEndpoint)

    if (description.startsWith("read attr")) {
        String attr = (msg.attrId ?: "").toString().toUpperCase()
        if (cluster == "0006" && attr == "0000" && msg.value != null) {
            handleOnOff(ep, msg.value.toString())
        } else {
            logDebug "Ignored attribute ${cluster}/${attr} = ${msg.value}"
        }
        return
    }

    if (description.startsWith("catchall")) {
        List data = (msg.data ?: []).collect { it.toString().toUpperCase() }
        String cmd = (msg.command ?: "").toString().toUpperCase()
        switch (cluster) {
            case "0006":
                if (cmd == "01" && data.size() >= 5 && data[0] == "00" && data[1] == "00" && data[2] == "00") {
                    handleOnOff(ep, data[4])   // read attributes response
                } else if (cmd == "0B" && data.size() >= 2 && data[1] != "00") {
                    log.warn "${device.displayName}: on/off command 0x${data[0]} failed on endpoint ${ep} (status 0x${data[1]})"
                } else if (cmd == "07") {
                    logDebug "Configure reporting response on endpoint ${ep}: ${data}"
                } else {
                    logDebug "On/off catchall cmd ${cmd} endpoint ${ep}: ${data}"
                }
                break
            case "0013":
                logInfo "Device announced (rejoined network)"
                runIn(3, "refresh")
                break
            case "8021":
                logDebug "Bind response: ${data}"
                break
            default:
                logDebug "Unhandled catchall cluster ${cluster} cmd ${cmd}: ${data}"
        }
        return
    }

    logDebug "Unhandled message: ${description}"
}

private void handleOnOff(Integer ep, String hexValue) {
    if (!(ep in [1, 2])) {
        logDebug "On/off report from unexpected endpoint ${ep}"
        return
    }
    Integer raw = hexToInt(hexValue)
    if (raw == null) return
    String v = raw != 0 ? "on" : "off"

    String key = "ep" + ep
    String prev = state[key]
    state[key] = v

    String side = (ep == 1) ? "Left" : "Right"
    sendEvent(name: (ep == 1 ? "leftSwitch" : "rightSwitch"), value: v, descriptionText: "${side} outlet is ${v}")
    if (prev != v) logInfo "${side} outlet is ${v}"

    def cd = getChildDevice(childDni(ep))
    if (cd) cd.parse([[name: "switch", value: v, descriptionText: "${cd.displayName} is ${v}"]])

    recomputeParentSwitch()
}

private void recomputeParentSwitch() {
    String l = state.ep1
    String r = state.ep2
    String v = null

    switch (settings.parentFollows ?: "left") {
        case "right":
            v = r
            break
        case "either":
            if (l == "on" || r == "on") v = "on"
            else if (l && r) v = "off"
            break
        case "both":
            if (l == "off" || r == "off") v = "off"
            else if (l && r) v = "on"
            break
        default:
            v = l
    }

    if (!v) return
    if (device.currentValue("switch") != v) logInfo "Parent switch is ${v}"
    sendEvent(name: "switch", value: v, descriptionText: "${device.displayName} is ${v}")
}

// ---------------------------------------------------------------------------
// Commands
// ---------------------------------------------------------------------------

void on() {
    sendOnOff(parentTargets(), true)
}

void off() {
    sendOnOff(parentTargets(), false)
}

void childOn(num) {
    Integer ep = validOutlet(num)
    if (ep) sendOnOff([ep], true)
}

void childOff(num) {
    Integer ep = validOutlet(num)
    if (ep) sendOnOff([ep], false)
}

void refresh() {
    logDebug "Refresh"
    sendZigbee(readOnOffCmds([1, 2]))
}

void ping() {
    logDebug "Ping"
    List cmds = readOnOffCmds([1])
    cmds << "delay 3000"
    cmds += readOnOffCmds([2])
    sendZigbee(cmds)
}

void identify() {
    logInfo "Identify (10 s)"
    sendZigbee(["he cmd 0x${dni()} 0x01 0x0003 0x00 {0A00}"])
}

void resetToFactoryDefault() {
    log.warn "${device.displayName}: sending Basic cluster reset-to-factory-defaults"
    sendZigbee(["he cmd 0x${dni()} 0x01 0x0000 0x00 {}"])
    runIn(5, "refresh")
}

// Called by children
void componentOn(cd) {
    Integer ep = endpointFromChild(cd)
    if (ep) sendOnOff([ep], true)
}

void componentOff(cd) {
    Integer ep = endpointFromChild(cd)
    if (ep) sendOnOff([ep], false)
}

void componentRefresh(cd) {
    Integer ep = endpointFromChild(cd)
    if (ep) sendZigbee(readOnOffCmds([ep]))
}

Boolean debugEnabled() {
    return settings.enableDebug == true
}

// ---------------------------------------------------------------------------
// Health
// ---------------------------------------------------------------------------

private void scheduleHealthCheck() {
    unschedule("healthCheck")
    Integer n = clamp(settings.healthInterval, 2, 1, 30)
    Integer sec = new Random().nextInt(60)
    schedule("${sec} */${n} * ? * *", "healthCheck")
    logDebug "Health check every ${n} min (offline after ${clamp(settings.healthMissed, 2, 1, 10)} unanswered pings)"
}

void healthCheck() {
    Long lastRx = (state.lastRx ?: 0L) as Long
    Long lastCheck = (state.lastCheck ?: 0L) as Long
    state.lastCheck = now()

    // Any traffic since the last check (report, command reply or ping reply) = alive
    if (lastRx > lastCheck) {
        state.missed = 0
        state.pingPending = false
        return
    }

    // Quiet since the last check. If that check sent a ping, the ping went unanswered.
    if (state.pingPending) {
        Integer limit = clamp(settings.healthMissed, 2, 1, 10)
        Integer missed = (((state.missed ?: 0) as Integer) + 1)
        state.missed = missed
        logDebug "Ping unanswered (${missed}/${limit})"
        if (missed >= limit) {
            // Final confirming ping; offline only if this one goes unanswered too
            state.confirmFrom = now()
            state.pingPending = true
            ping()
            runIn(CONFIRM_SECS, "confirmOffline")
            return
        }
    } else {
        logDebug "No traffic since last check - pinging"
    }

    state.pingPending = true
    ping()
}

void confirmOffline() {
    Long lastRx = (state.lastRx ?: 0L) as Long
    Long from = (state.confirmFrom ?: 0L) as Long
    if (lastRx > from) {
        logDebug "Confirming ping answered - still online"
        return
    }
    setHealth("offline")
}

private void markAlive() {
    state.lastRx = now()
    state.missed = 0
    state.pingPending = false
    if (device.currentValue("healthStatus") != "online") setHealth("online")

    Long lastEvt = (state.lastCheckinEvt ?: 0L) as Long
    if (now() - lastEvt >= 60000L) {
        state.lastCheckinEvt = now()
        String ts = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
        sendEvent(name: "lastCheckin", value: ts)
        pushToChildren([name: "lastCheckin", value: ts])
    }
}

private void setHealth(String v) {
    String prev = device.currentValue("healthStatus")
    if (prev == v) return
    sendEvent(name: "healthStatus", value: v, descriptionText: "${device.displayName} is ${v}")
    pushToChildren([name: "healthStatus", value: v])
    if (v == "offline") {
        log.warn "${device.displayName} is offline"
    } else {
        logInfo "healthStatus is ${v}"
    }
    if (v == "online" && prev == "offline") runIn(3, "refresh")
}

// ---------------------------------------------------------------------------
// Children
// ---------------------------------------------------------------------------

private void createChildDevices() {
    [1, 2].each { Integer ep ->
        String cdni = childDni(ep)
        if (!getChildDevice(cdni)) {
            String label = (ep == 1) ? "Left GPO" : "Right GPO"
            addChildDevice("mr_fy_hubitat", "Nue Child GPO", cdni, [label: label, isComponent: true])
            logInfo "Created child ${label}"
        }
    }
}

private void pushToChildren(Map evt) {
    getChildDevices().each { cd -> cd.parse([evt]) }
}

private String childDni(Integer ep) {
    return "${device.deviceNetworkId}-${ep}".toString()
}

private Integer endpointFromChild(cd) {
    try {
        Integer ep = cd.deviceNetworkId.tokenize("-").last() as Integer
        if (ep in [1, 2]) return ep
    } catch (Exception ignored) { }
    log.error "${device.displayName}: cannot determine endpoint for child ${cd?.deviceNetworkId}"
    return null
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private List<Integer> parentTargets() {
    switch (settings.parentFollows ?: "left") {
        case "right":  return [2]
        case "either":
        case "both":   return [1, 2]
        default:       return [1]
    }
}

private void sendOnOff(List<Integer> eps, Boolean turnOn) {
    List cmds = []
    eps.each { Integer ep ->
        cmds << "he cmd 0x${dni()} 0x${hex2(ep)} 0x0006 ${turnOn ? '0x01' : '0x00'} {}"
        cmds << "delay 300"
    }
    cmds << "delay 1200"
    cmds += readOnOffCmds(eps)
    logDebug "Outlet(s) ${eps} -> ${turnOn ? 'on' : 'off'}"
    sendZigbee(cmds)
}

private List readOnOffCmds(List<Integer> eps) {
    List cmds = []
    eps.each { Integer ep ->
        cmds << "he rattr 0x${dni()} 0x${hex2(ep)} 0x0006 0x0000 {}"
        cmds << "delay 500"
    }
    return cmds
}

private void sendZigbee(List cmds) {
    List<String> out = cmds.collect { it.toString() }
    while (out && out[-1].startsWith("delay")) out.remove(out.size() - 1)
    if (!out) return
    logDebug "Sending: ${out}"
    sendHubCommand(new hubitat.device.HubMultiAction(out, hubitat.device.Protocol.ZIGBEE))
}

private Integer validOutlet(num) {
    Integer ep = null
    try { ep = num as Integer } catch (Exception ignored) { }
    if (ep in [1, 2]) return ep
    log.error "${device.displayName}: invalid outlet number '${num}' (use 1 or 2)"
    return null
}

private String dni() {
    return device.deviceNetworkId
}

private static String hex2(Integer v) {
    return Integer.toHexString(v).padLeft(2, "0").toUpperCase()
}

private static Integer hexToInt(Object h) {
    if (h == null) return null
    try {
        return Integer.parseInt(h.toString(), 16)
    } catch (Exception ignored) {
        return null
    }
}

private static Integer clamp(Object v, Integer dflt, Integer lo, Integer hi) {
    Integer n = dflt
    try { if (v != null) n = v as Integer } catch (Exception ignored) { }
    return Math.max(lo, Math.min(hi, n))
}

private void logInfo(String msg) {
    if (settings.enableInfo != false) log.info "${device.displayName}: ${msg}"
}

private void logDebug(String msg) {
    if (settings.enableDebug) log.debug "${device.displayName}: ${msg}"
}
