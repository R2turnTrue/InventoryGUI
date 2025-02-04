package net.projecttl.inventory.gui

import net.kyori.adventure.text.Component
import net.projecttl.inventory.InventoryGUI.inventoryIds
import net.projecttl.inventory.InventoryGUI.plugin
import net.projecttl.inventory.util.InventoryType
import net.projecttl.inventory.util.LinkedSlot
import net.projecttl.inventory.util.ObservableHashMap
import net.projecttl.inventory.util.SlotHandler
import org.bukkit.Bukkit
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * build 함수 실행 후에도 인벤토리를 수정할 수 있는 InventoryBuilder 입니다.
 */
data class LinkedInventoryBuilder(override val player: Player, override val slotType: InventoryType, override val title: Component): Listener,
    InventoryBuilder {

    override val slots = ObservableHashMap<Int, LinkedSlot>()
    override val id: UUID = UUID.randomUUID()
    override lateinit var inventory: Inventory
        private set
        
    val closeHandlers = ArrayList<InventoryCloseEvent.() -> Unit>()

    init {
        inventoryIds[id] = this
    }

    override fun slot(slot: Int, item: ItemStack, handler: InventoryClickEvent.() -> Unit) {
        slots[slot] = LinkedSlot(item, SlotHandler().apply { onClick(handler) })
    }
    
    override fun onClose(handler: InventoryCloseEvent.() -> Unit) {
        closeHandlers.add(handler)
    }

    override fun slot(slot: Int, item: ItemStack) {
        slot(slot, item) {}
    }

    override fun close() {
        if (this::inventory.isInitialized)
            inventory.close()
    }

    override fun build(): Inventory {
        inventory = Bukkit.createInventory(null, slotType.size, title)

        for (slot in slots.entries) {
            inventory.setItem(slot.key, slot.value.stack)
        }

        slots.addObserver {
            inventory.setItem(it.first, it.second.stack)
        }

        player.openInventory(inventory)
        Bukkit.getServer().pluginManager.registerEvents(this, plugin)
        return inventory
    }

    override fun destroy() {

    }

    @EventHandler
    private fun listener(event: InventoryClickEvent) {
        if(event.view.title() == this.title) {
            if (inventoryIds.contains(id) && event.currentItem != null && event.view.player == player) {
                if (event.inventory == inventory) {
                    for (slot in slots.entries) {
                        if (slot.key == event.rawSlot){
                            event.isCancelled = true
                            slot.value.handler.click.forEach {
                                it(event)
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private fun listener2(event: InventoryMoveItemEvent) {
        if (inventoryIds.contains(id) && event.source.holder?.inventory?.viewers?.contains(player)!!
            && event.source.holder is Container && (event.source.holder as Container).customName() == this.title
        )
            event.isCancelled = true
    }

    @EventHandler
    private fun listener3(event: InventoryCloseEvent) {
        for(closeHandler in closeHandlers) {
            closeHandler(event)
        }
        if (event.view.player == player && inventoryIds.contains(id))
            inventoryIds.remove(id)
    }

    @EventHandler
    private fun listener4(event: PlayerSwapHandItemsEvent) {
        if (event.player.inventory == inventory) {
            event.isCancelled = true

        }
    }
}
