package com.gui.iai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopTest {

    @Test
    fun itensGratisJaSaoDoJogador() {
        assertTrue(Shop.isOwned(ShopItem.OBI_SHU, emptySet()))
        assertTrue(Shop.isOwned(ShopItem.BLADE_HAGANE, emptySet()))
    }

    @Test
    fun compraDescontaMoedasEGuardaOItem() {
        val purchase = Shop.buy(ShopItem.OBI_AI, coins = 100, owned = emptySet())
        assertNotNull(purchase)
        assertEquals(100 - ShopItem.OBI_AI.price, purchase!!.coins)
        assertTrue(ShopItem.OBI_AI.id in purchase.owned)
    }

    @Test
    fun naoCompraSemMoedasNemDuasVezes() {
        assertNull(Shop.buy(ShopItem.BLADE_KAGE, coins = 10, owned = emptySet()))
        assertNull(Shop.buy(ShopItem.OBI_AI, coins = 999, owned = setOf(ShopItem.OBI_AI.id)))
        assertNull(Shop.buy(ShopItem.OBI_SHU, coins = 999, owned = emptySet()))
    }

    @Test
    fun contaSoOsItensPagos() {
        assertEquals(2, Shop.purchasedCount(setOf(ShopItem.OBI_AI.id, ShopItem.BLADE_RAI.id, "inexistente")))
    }
}
