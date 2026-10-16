package io.github.legendaryredfox.foxhg.core.data.personal

import io.github.legendaryredfox.foxhg.core.data.evolution.EvolutionData

/** Edits of the TM/HM compatibility bits of many species at once. */
object TmHmBulk {
    /** Species (record numbers, 1 and up) that can learn machine [machine] (0 to 99: TM01 to TM92, then HM01 to HM08). */
    fun learners(table: PersonalTable, machine: Int): List<Int> =
        (1 until table.records.size).filter { table.records[it].learnsTmHm(machine) }

    /** [table] with machine [machine] set to [learns] for every species in [species]; others stay. */
    fun set(table: PersonalTable, machine: Int, species: Collection<Int>, learns: Boolean): PersonalTable {
        val targets = species.toSet()
        var result = table
        for (s in targets) {
            val record = table.records.getOrNull(s) ?: continue
            if (record.learnsTmHm(machine) != learns) result = result.with(s, record.withTmHm(machine, learns))
        }
        return result
    }

    /** [table] with every machine of [from] copied to every species in [to]: [to] ends up with exactly the machines [from] has, or with the union of both when [union]. */
    fun copy(table: PersonalTable, from: Int, to: Collection<Int>, union: Boolean = false): PersonalTable {
        val source = table.records.getOrNull(from) ?: return table
        var result = table
        for (s in to.toSet()) {
            if (s == from) continue
            var record = table.records.getOrNull(s) ?: continue
            for (machine in 0 until MACHINES) {
                val has = source.learnsTmHm(machine)
                if (has || !union) record = record.withTmHm(machine, has || (union && record.learnsTmHm(machine)))
            }
            if (record != table.records[s]) result = result.with(s, record)
        }
        return result
    }

    /** Species grouped into evolution families (everything linked by an evolution entry), each sorted; species with no link are alone. */
    fun families(evolutions: List<EvolutionData>): List<List<Int>> {
        val parent = IntArray(evolutions.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; var c = x; while (parent[c] != r) { val n = parent[c]; parent[c] = r; c = n }; return r }
        evolutions.forEachIndexed { species, data ->
            for (e in data.slots) if (e.method != 0 && e.target in 1 until evolutions.size) parent[find(species)] = find(e.target)
        }
        return (1 until evolutions.size).groupBy { find(it) }.values.map { it.sorted() }.sortedBy { it.first() }
    }

    const val MACHINES = 100
}
