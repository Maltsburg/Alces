package me.akoot.plugins

import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.meta.components.FoodComponent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class Alces : JavaPlugin(), Listener {

    private lateinit var serializer: GsonComponentSerializer

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        serializer = GsonComponentSerializer.gson()
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.block
        if (!isHead(block)) return

        val key = getKey(block.location)
        val pdc = block.chunk.persistentDataContainer

        val itemMeta = event.itemInHand.itemMeta

        val food: String? = itemMeta.food.takeIf { itemMeta.hasFood() }?.let { getFood(it) }

        val displayName = itemMeta.displayName()?.let { serializer.serialize(it) }
        val lore = itemMeta.lore()?.mapNotNull { serializer.serialize(it) }?.joinToString("\n")

        if (displayName == null && lore == null && food.isNullOrBlank()) return

        pdc.set(key, PersistentDataType.STRING, "${food ?: "-"}\n${displayName ?: "-"}\n${lore ?: "-"}")
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        if (!isHead(block)) return

        val key = getKey(block.location)
        val pdc = block.chunk.persistentDataContainer
        val data = pdc.get(key, PersistentDataType.STRING) ?: return

        event.isDropItems = false

        val lines = data.split("\n")
        val food = lines[0]
        val displayName = lines[1]
        val lore = lines.drop(2)

        val drop = block.drops.first()
        val itemMeta = drop.itemMeta

        if (displayName != "-")
            itemMeta.displayName(serializer.deserialize(displayName))

        if (lines[1] != "-")
            itemMeta.lore(lore.map { serializer.deserialize(it) })

        if (food != "-") {
            val foodComponent: FoodComponent = itemMeta.food
            setFood(foodComponent, food)
            itemMeta.setFood(foodComponent)
        }

        drop.itemMeta = itemMeta

        block.location.world.dropItemNaturally(block.location, drop)
        pdc.remove(key)
    }

    private fun isHead(block: Block): Boolean {
        return block.type.name.endsWith("_HEAD")
    }

    private fun getKey(location: Location): NamespacedKey {
        val key = "${location.world.name}.${location.blockX}.${location.blockY}.${location.blockZ}"
        return NamespacedKey("alces", key)
    }

    private fun getFood(food: FoodComponent?): String? {
        return food?.let {
            "hunger:${it.nutrition}," + "sat:${it.saturation}," + "snack:${it.canAlwaysEat()}"
        }
    }

    private fun setFood(foodComponent: FoodComponent, foodProperties: String) {
        val properties = foodProperties.split(",")
            .map { it.split(":") }
            .associate { it[0] to it[1] }

        foodComponent.nutrition = properties["hunger"]?.toIntOrNull() ?: return
        foodComponent.saturation = properties["sat"]?.toFloatOrNull() ?: return
        foodComponent.setCanAlwaysEat(properties["snack"]?.toBoolean() ?: return)
    }
}