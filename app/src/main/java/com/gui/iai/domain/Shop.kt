package com.gui.iai.domain

enum class ItemSlot(val title: String) {
    OBI("Faixas"),
    BLADE("Lâminas")
}

/**
 * Itens da loja. A cor fica em ARGB para o domínio não depender do Compose.
 * Faixa muda a cor do obi e das fitas; lâmina muda o rastro e o corte na tela.
 */
enum class ShopItem(
    val id: String,
    val slot: ItemSlot,
    val title: String,
    val kanji: String,
    val price: Int,
    val argb: Long,
    val description: String
) {
    OBI_SHU("obi_shu", ItemSlot.OBI, "Faixa Rubra", "紅", 0, 0xFFB8322A, "A faixa de todo iniciante."),
    OBI_AI("obi_ai", ItemSlot.OBI, "Faixa Índigo", "藍", 60, 0xFF3E5C8A, "Tingida com índigo do vale."),
    OBI_SHIRO("obi_shiro", ItemSlot.OBI, "Faixa Branca", "白", 80, 0xFFF1E7D0, "Pura como a primeira neve."),
    OBI_SAKURA("obi_sakura", ItemSlot.OBI, "Faixa Sakura", "桜", 100, 0xFFE095AA, "Para quem corta com leveza."),
    OBI_KIN("obi_kin", ItemSlot.OBI, "Faixa Dourada", "金", 160, 0xFFD4A84A, "Bordada com fios de ouro."),
    OBI_YORU("obi_yoru", ItemSlot.OBI, "Faixa da Noite", "夜", 240, 0xFF6A4BA0, "Dizem que era do próprio Kage."),

    BLADE_HAGANE("blade_hagane", ItemSlot.BLADE, "Aço Polido", "鋼", 0, 0xFFFFFFFF, "Brilho limpo de aço."),
    BLADE_SAKURA("blade_sakura", ItemSlot.BLADE, "Pétala Cortante", "桜", 120, 0xFFFFB7CC, "O corte deixa um rastro rosa."),
    BLADE_KIN("blade_kin", ItemSlot.BLADE, "Lâmina de Ouro", "金", 150, 0xFFFFD36B, "Reflete o sol do entardecer."),
    BLADE_RAI("blade_rai", ItemSlot.BLADE, "Raio Azul", "雷", 210, 0xFF8FD3FF, "Rápida como um trovão."),
    BLADE_HONO("blade_hono", ItemSlot.BLADE, "Chama", "炎", 270, 0xFFFF7A3D, "Forjada no vulcão."),
    BLADE_KAGE("blade_kage", ItemSlot.BLADE, "Sombra", "影", 340, 0xFFB399FF, "Um rastro roxo e silencioso.");

    val free: Boolean get() = price == 0

    companion object {
        fun fromId(id: String?): ShopItem? = entries.firstOrNull { it.id == id }

        val defaultObi = OBI_SHU
        val defaultBlade = BLADE_HAGANE

        fun ofSlot(slot: ItemSlot): List<ShopItem> = entries.filter { it.slot == slot }
    }
}

/** Itens equipados. */
data class Loadout(
    val obi: ShopItem = ShopItem.defaultObi,
    val blade: ShopItem = ShopItem.defaultBlade
)

/** Resultado de uma compra: o novo saldo e os itens que o jogador tem. */
data class Purchase(val coins: Int, val owned: Set<String>)

object Shop {
    fun isOwned(item: ShopItem, owned: Set<String>): Boolean = item.free || item.id in owned

    /** Compra o item, ou devolve null se já tem ou não tem moedas suficientes. */
    fun buy(item: ShopItem, coins: Int, owned: Set<String>): Purchase? {
        if (isOwned(item, owned)) return null
        if (coins < item.price) return null
        return Purchase(coins - item.price, owned + item.id)
    }

    /** Quantos itens pagos o jogador já comprou. */
    fun purchasedCount(owned: Set<String>): Int =
        ShopItem.entries.count { !it.free && it.id in owned }
}
