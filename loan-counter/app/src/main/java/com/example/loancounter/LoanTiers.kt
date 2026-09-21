package com.example.loancounter

data class Tier(val emoji: String, val name: String, val price: Long)

object LoanTiers {

    // (달성 퍼센트, 이모지, 문구) — 원금 대비 비율이라 대출 규모와 무관하게 항상 자연스럽게 맞음
    private val milestones: List<Triple<Double, String, String>> = listOf(
        Triple(2.0, "👣", "첫 상환 시작"),
        Triple(5.0, "🌱", "티끌 모아 태산의 시작"),
        Triple(10.0, "🔟", "원금 10% 상환"),
        Triple(20.0, "🎯", "1/5 완료"),
        Triple(25.0, "🍕", "4분의 1 완주"),
        Triple(33.0, "🥉", "1/3 돌파"),
        Triple(40.0, "🚶", "40% 지점"),
        Triple(50.0, "🎉", "반환점 통과!"),
        Triple(60.0, "🏃", "60% 지점"),
        Triple(66.0, "🥈", "2/3 돌파"),
        Triple(75.0, "🍰", "4분의 3 완주"),
        Triple(80.0, "🏔️", "막바지 진입"),
        Triple(90.0, "🔥", "90% — 거의 다 옴"),
        Triple(95.0, "✨", "마지막 스퍼트"),
        Triple(99.0, "🫡", "마지막 한 걸음"),
        Triple(100.0, "🏁", "완납! 자유!")
    )

    /** 현재 대출원금 기준으로 절대 금액 리스트를 만들어 반환 (오름차순) */
    fun forPrincipal(principalManwon: Long): List<Tier> {
        val principalWon = principalManwon * 10_000.0
        return milestones.map { (pct, emoji, name) ->
            Tier(emoji, name, (principalWon * pct / 100.0).toLong())
        }
    }

    fun currentIndex(items: List<Tier>, repaidWon: Long): Int {
        var idx = -1
        for (i in items.indices) {
            if (repaidWon >= items[i].price) idx = i else break
        }
        return idx
    }
}
