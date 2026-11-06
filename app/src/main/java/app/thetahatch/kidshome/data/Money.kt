package app.thetahatch.kidshome.data

import android.content.Context
import org.json.JSONObject

/** おみせやさん ごっこ の お金 と しょうひん */
object Money {
    /** 大きい じゅん（千円札・500 円 … 1 円） */
    val DENOMS = listOf(1000, 500, 100, 50, 10, 5, 1)

    fun sum(m: Map<Int, Int>) = m.entries.sumOf { it.key * it.value }

    /** いちばん 少ない まい数 で（おつり の だしかた） */
    fun breakdown(amount: Int): Map<Int, Int> {
        var rest = amount
        val out = LinkedHashMap<Int, Int>()
        for (d in DENOMS) { val n = rest / d; if (n > 0) { out[d] = n; rest -= n * d } }
        return out
    }

    /** 消費税 10%（1 円 未満 は きりすて） */
    fun withTax(price: Int) = price * 110 / 100

    /** レベル で ねだん を かえる：0 は 10 円 きざみ・100 円 まで、1 は そのまま、2 は 税ぬき（レジ で 10%） */
    fun price(base: Int, level: Int) = when (level) {
        0 -> ((base + 5) / 10 * 10).coerceIn(10, 100)
        else -> base
    }

    fun total(prices: List<Int>, level: Int) = if (level >= 2) withTax(prices.sum()) else prices.sum()

    /** おきゃくさん が だす お金（ぴったり・きり の いい 数・千円札） */
    fun customerPays(total: Int, pick: Int): Int {
        val options = listOf(total, (total + 99) / 100 * 100, (total + 499) / 500 * 500, (total + 999) / 1000 * 1000).distinct().filter { it >= total }
        return options[pick.mod(options.size)]
    }

    /** real：本物 の ねだん（ラベル の QR から。レベル で まるめ ない） */
    data class Item(val emoji: String, val name: String, val price: Int, val real: Boolean = false)

    /** QR ラベル の しょうひん */
    fun fromLabel(i: ShopItems.Item) = Item("🏷️", i.name, i.price, real = true)
    data class Shop(val id: String, val name: String, val emoji: String, val items: List<Item>)

    private var shops: List<Shop>? = null

    /** assets/content/shop.json（あとで SD の KidsHome/content/shop.json を 優先 に できる） */
    fun shops(c: Context): List<Shop> = shops ?: runCatching {
        val a = JSONObject(c.assets.open("content/shop.json").bufferedReader().readText()).getJSONArray("shops")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val items = o.getJSONArray("items").let { it2 -> (0 until it2.length()).map { j -> it2.getJSONArray(j).let { x -> Item(x.getString(0), x.getString(1), x.getInt(2)) } } }
            Shop(o.getString("id"), o.getString("name"), o.getString("emoji"), items)
        }
    }.getOrDefault(emptyList()).also { shops = it }
}
