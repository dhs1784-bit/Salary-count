package com.example.severancecounter

data class Tier(val emoji: String, val name: String, val price: Long)

object SeveranceTiers {

    // 소비 욕구가 아니라 "안전망/자유"를 상징하는 아이템으로 구성.
    // 초반엔 가볍게, 갈수록 진짜 버팀목이 되는 금액으로.
    val items: List<Tier> = listOf(
        Tier("🍗", "치킨 한마리 값 적립", 20_000),
        Tier("🎬", "넷플릭스 3개월치", 40_000),
        Tier("💰", "비상금 10만원 마련", 100_000),
        Tier("💊", "갑작스런 병원비 대응 가능", 200_000),
        Tier("🛠️", "예상 밖 수리비 대응 가능", 300_000),
        Tier("🍚", "무급으로 일주일 버틸 돈", 500_000),
        Tier("🏠", "월세 한 달치 확보", 800_000),
        Tier("🍜", "한 달 생활비 확보", 1_500_000),
        Tier("✈️", "훌쩍 떠날 여행 경비", 2_000_000),
        Tier("📚", "자격증·교육에 투자할 여유", 3_000_000),
        Tier("🧳", "퇴사해도 한 달은 버틸 돈", 5_000_000),
        Tier("🛡️", "이직 준비 3개월 버팀목", 15_000_000),
        Tier("🛡️", "이직 준비 6개월 버팀목", 30_000_000),
        Tier("🏦", "전세자금 일부 마련", 50_000_000),
        Tier("🌱", "창업 종잣돈", 80_000_000),
        Tier("🕊️", "1년치 생활비 확보", 120_000_000),
        Tier("🏝️", "완전한 자유 자금", 200_000_000)
    ).sortedBy { it.price }

    fun currentIndex(earned: Long): Int {
        var idx = -1
        for (i in items.indices) {
            if (earned >= items[i].price) idx = i else break
        }
        return idx
    }
}
