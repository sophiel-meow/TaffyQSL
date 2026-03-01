package moe.zzy040330.taffyqsl.data.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

class ConfigRepository private constructor(context: Context) {

    private val appContext = context.applicationContext

    val bands: List<Band> by lazy { loadBands() }
    val modes: List<Mode> by lazy { loadModes() }
    val dxccEntities: List<DxccEntity> by lazy { loadDxccEntities() }
    val satellites: List<Satellite> by lazy { loadSatellites() }
    val propModes: List<PropMode> by lazy { loadPropModes() }
    val satelliteInfoList: List<SatelliteInfo> by lazy { loadSatelliteInfo() }

    // ADIF mode & submode to TQSL mode
    val adifModeMap: Map<Pair<String, String>, String> by lazy { loadAdifModeMap() }

    private val subdivisionFields: List<SubdivisionField> by lazy { loadSubdivisions() }

    private fun readJson(filename: String): String =
        appContext.assets.open(filename).bufferedReader(Charsets.UTF_8).readText()

    private fun loadBands(): List<Band> {
        val arr = JSONArray(readJson("bands.json"))
        val result = mutableListOf<Band>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                Band(
                    name = obj.getString("name"),
                    spectrum = obj.getString("spectrum"),
                    freqLow = obj.getDouble("freqLow"),
                    freqHigh = obj.getDouble("freqHigh")
                )
            )
        }
        return result   // already sorted by conversion script
    }

    private fun loadModes(): List<Mode> {
        val arr = JSONArray(readJson("modes.json"))
        val result = mutableListOf<Mode>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.getString("name")
            result.add(
                Mode(
                    name = name,
                    group = obj.optString("group", ""),
                    adifMode = name
                )
            )
        }
        return result
    }

    private fun loadAdifModeMap(): Map<Pair<String, String>, String> {
        val arr = JSONArray(readJson("adifmap.json"))
        val result = mutableMapOf<Pair<String, String>, String>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val adifMode = obj.getString("adifMode").uppercase()
            val adifSubmode = obj.optString("adifSubmode", "").uppercase()
            val tqslMode = obj.getString("tqslMode")
            result[Pair(adifMode, adifSubmode)] = tqslMode
        }
        return result
    }

    private fun loadDxccEntities(): List<DxccEntity> {
        val arr = JSONArray(readJson("dxcc.json"))
        val result = mutableListOf<DxccEntity>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                DxccEntity(
                    arrlId = obj.getInt("arrlId"),
                    name = obj.getString("name"),
                    deleted = obj.optBoolean("deleted", false),
                    zoneMap = obj.optString("zoneMap", ""),
                    stateFieldId = obj.optString("stateFieldId", "")
                )
            )
        }
        return result
    }

    private fun loadSatellites(): List<Satellite> {
        val arr = JSONArray(readJson("satellites.json"))
        val result = mutableListOf<Satellite>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                Satellite(
                    name = obj.getString("name"),
                    description = obj.optString("description", ""),
                    startDate = obj.optString("startDate", ""),
                    endDate = obj.optString("endDate", "")
                )
            )
        }
        return result
    }

    private fun loadPropModes(): List<PropMode> {
        val arr = JSONArray(readJson("propmodes.json"))
        val result = mutableListOf<PropMode>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(
                PropMode(
                    name = obj.getString("name"),
                    description = obj.optString("description", "")
                )
            )
        }
        return result
    }

    private fun loadSubdivisions(): List<SubdivisionField> {
        val arr = JSONArray(readJson("subdivisions.json"))
        val result = mutableListOf<SubdivisionField>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val fieldId = obj.getString("fieldId")
            val label = obj.optString("label", "")
            val dependsOn = if (obj.isNull("dependsOn")) null else obj.optString("dependsOn")

            val groupsArr = obj.getJSONArray("groups")
            val groups = mutableListOf<SubdivisionGroup>()
            for (j in 0 until groupsArr.length()) {
                val gObj = groupsArr.getJSONObject(j)

                // dependency can be: int (DXCC ID), string (province abbrev), or null
                // Normalise to String? for int and string are comparable
                val dep: String? = if (gObj.isNull("dependency")) {
                    null
                } else {
                    try {
                        gObj.getInt("dependency").toString()  // int ID
                    } catch (e: JSONException) {
                        gObj.getString("dependency")  // string abbr
                    }
                }

                val valuesArr = gObj.getJSONArray("values")
                val values = mutableListOf<StateValue>()
                for (k in 0 until valuesArr.length()) {
                    val vObj = valuesArr.getJSONObject(k)
                    values.add(
                        StateValue(
                            abbrev = vObj.getString("abbrev"),
                            name = vObj.getString("name"),
                            cqZone = vObj.optString("cqZone", ""),
                            ituZone = vObj.optString("ituZone", "")
                        )
                    )
                }
                groups.add(SubdivisionGroup(dependency = dep, values = values))
            }
            result.add(
                SubdivisionField(
                    fieldId = fieldId,
                    label = label,
                    dependsOn = dependsOn,
                    groups = groups
                )
            )
        }
        return result
    }

    /**
     * Load subdivision values (states, provinces, oblasts, etc.) for a given field.
     *
     * @param fieldId  field ID (e.g. "US_STATE", "CA_PROVINCE").
     * @param dxccId   DXCC entity ID of the station.
     *                  - If the field has DXCC-grouped enums (e.g. US_STATE has separate
     *                      lists for USA/Alaska/Hawaii), returns only the relevant group.
     *                  - If the field has a flat list (e.g. CA_PROVINCE, CN_PROVINCE),
     *                      returns all values regardless of dxccId.
     *                  - Pass 0 to always return all values (flattened across groups).
     */
    fun loadStateValues(fieldId: String, dxccId: Int = 0): List<StateValue> {
        val field = subdivisionFields.firstOrNull { it.fieldId == fieldId } ?: return emptyList()

        if (dxccId != 0) {
            // Try to find a group that exactly matches the DXCC ID (stored as string)
            val exactMatch = field.groups.firstOrNull { it.dependency == dxccId.toString() }
            if (exactMatch != null) return exactMatch.values

            // Fall back to groups with no DXCC dependency (flat lists like CA_PROVINCE)
            val nullGroups = field.groups.filter { it.dependency == null }
            if (nullGroups.isNotEmpty()) return nullGroups.flatMap { it.values }

            return emptyList()
        }

        // dxccId == 0: return all values flattened
        return field.groups.flatMap { it.values }
    }

    /**
     * Load subdivision values for a secondary field (county, park, city, etc.)
     * by the selected value of its parent field.
     *
     * @param fieldId       secondary field ID (e.g. "US_COUNTY", "JA_CITY_GUN_KU").
     * @param parentValue   selected abbr of the parent field (e.g. "AL", "01").
     */
    fun loadSubValues(fieldId: String, parentValue: String): List<StateValue> {
        if (parentValue.isEmpty()) return emptyList()
        val field = subdivisionFields.firstOrNull { it.fieldId == fieldId } ?: return emptyList()
        val exactMatch = field.groups.firstOrNull { it.dependency == parentValue }
        if (exactMatch != null) return exactMatch.values
        val nullGroups = field.groups.filter { it.dependency == null }
        return nullGroups.flatMap { it.values }
    }

    /**
     * Return subdivision fields that depend on the given primary field.
     * e.g. for secondaryFields("US_STATE") returns [US_COUNTY, US_PARK].
     */
    fun secondaryFields(primaryFieldId: String): List<SubdivisionField> =
        subdivisionFields.filter { it.dependsOn == primaryFieldId }

    /**
     * Map ADIF mode+submode to TQSL mode name
     */
    fun resolveTqslMode(adifMode: String, adifSubmode: String): String {
        val key = Pair(adifMode.uppercase(), adifSubmode.uppercase())
        return adifModeMap[key]
            ?: adifModeMap[Pair(adifMode.uppercase(), "")]
            ?: adifMode.uppercase()
    }

    /**
     * Auto-infer band by frequency (MHz)
     */
    fun bandForFreq(freqMhz: Double): String? =
        bands.firstOrNull { freqMhz >= it.freqLow && freqMhz <= it.freqHigh }?.name

    fun dxccById(id: Int): DxccEntity? = dxccEntities.firstOrNull { it.arrlId == id }

    /**
     * Find a SatelliteInfo by name or alias.
     * Matching is case-insensitive and ignores hyphens and underscores.
     * e.g. "SO50", "SO-50", "so50" all resolve to SO-50.
     */
    fun findSatelliteByAlias(token: String): SatelliteInfo? {
        val normalized = token.uppercase().replace("-", "").replace("_", "")
        return satelliteInfoList.firstOrNull { sat ->
            sat.name.uppercase().replace("-", "").replace("_", "") == normalized ||
                sat.aliases.any { alias ->
                    alias.uppercase().replace("-", "").replace("_", "") == normalized
                }
        }
    }

    private fun loadSatelliteInfo(): List<SatelliteInfo> {
        val arr = JSONArray(readJson("satellites_info.json"))
        val result = mutableListOf<SatelliteInfo>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val aliasArr = obj.getJSONArray("aliases")
            val aliases = (0 until aliasArr.length()).map { aliasArr.getString(it) }
            result.add(
                SatelliteInfo(
                    name = obj.getString("name"),
                    aliases = aliases,
                    downlinkFreqMhz = obj.getDouble("downlinkFreqMhz"),
                    uplinkFreqMhz = obj.getDouble("uplinkFreqMhz"),
                    mode = obj.optString("mode", "")
                )
            )
        }
        return result
    }

    companion object {
        @Volatile
        private var instance: ConfigRepository? = null

        fun getInstance(context: Context): ConfigRepository =
            instance ?: synchronized(this) {
                instance ?: ConfigRepository(context).also { instance = it }
            }
    }
}
