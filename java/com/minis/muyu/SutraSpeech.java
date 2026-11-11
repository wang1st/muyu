package com.minis.muyu;

/**
 * 誦讀讀音：依《金剛經誦讀筆記》的分級注音方案，把易誤讀的字換成同音字再交給 TTS。
 * 單字發音，故用「同音字替換」而非拼音標注；多音字按上下文判定。
 */
public final class SutraSpeech {
    private SutraSpeech() {}

    /** 取第 idx 個字的誦讀寫法（同音字），交 TTS 發音 */
    public static String at(String sutra, int idx) {
        if (sutra == null || idx < 0 || idx >= sutra.length()) return "";
        char ch = sutra.charAt(idx);
        char prev = idx > 0 ? sutra.charAt(idx - 1) : 0;
        char next = idx + 1 < sutra.length() ? sutra.charAt(idx + 1) : 0;
        switch (ch) {
            // 甲類：梵漢對音
            case '般': return "波";                       // 般若 bō rě
            case '若': return prev == '般' ? "惹" : "若";  // rě / ruò
            case '藐': return "秒";                       // miǎo
            case '訶': return "喝";                       // hē
            case '祇': return "奇";                       // qí（祇樹、阿僧祇）
            case '洹': return "环";                       // huán
            case '那': return "纳";                       // nà
            case '昧': return "妹";                       // mèi
            case '塞': return "腮";                       // sāi（優婆塞）
            case '給': return "几";                       // jǐ（給孤獨園）
            case '舍': return (next == '衛' || next == '利') ? "设" : "舍";  // 舍衛國、舍利子 shè
            case '蜜': return "密";                       // 波羅蜜 mì
            case '槃': return "盘";                       // 涅槃 pán
            // 乙類：破讀別義
            case '降': return "翔";                       // 降伏 xiáng
            case '荷': return "贺";                       // 荷擔 hè
            case '樂': return "药";                       // 願樂 yào
            case '種': return (next == '諸' || next == '善') ? "众" : "种";  // 種善根 zhòng
            case '華': return "花";                       // 華香 huā
            case '供': return next == '養' ? "共" : "供";  // 供養 gòng
            case '著': return "浊";                       // 著衣 zhuó
            case '轉': return "转";                       // 轉輪 zhuǎn
            // 丙類：形近訛讀
            case '伽': return prev == '殑' ? "嘎" : "茄";  // 殑伽 gā / 薄伽梵、補特伽羅 qié
            case '薄': return next == '伽' ? "博" : "薄";  // 薄伽梵 bó
            case '殑': return "擎";                       // qíng
            case '蒭': return "除";                       // 苾蒭 bì chú
            case '鄔': return "屋";                       // 鄔波… wū
            case '曇': return "谈";                       // 尼煞曇 tán
            case '胝': return "知";                       // 俱胝 zhī
            case '庾': return "与";                       // 那庾多 yǔ
            case '尒': return "尔";                       // 尒＝爾
            case '筭': return "算";                       // 筭＝算
            case '趺': return "夫";                       // 結加趺坐 fū
            case '遶': return "绕";                       // 右遶 rào
            case '剎': return "岔";                       // chà
            case '㝡': return "最";                       // 㝡＝最
            case '罣': return "挂";                       // 心無罣礙 guà
            case '埵': return "朵";                       // 菩提薩埵 duǒ
            case '缽': return "波";                       // bō
            case '諦': return "弟";                       // dì
            case '訖': return "气";                       // qì
            case '敷': return "夫";                       // fū
            case '袒': return "毯";                       // tǎn
            case '膝': return "西";                       // xī
            case '囑': return "主";                       // zhǔ
            case '筏': return "罚";                       // fá
            case '長': return next == '老' ? "掌" : "常";   // 長老 zhǎng / 人身長大 cháng
            case '還': return "环";                       // 還至本處 huán（TTS 默認 hái）
            case '寧': return "佞";                       // 寧為多不 nìng（TTS 默認 níng）
            case '分': return (prev == '日' || prev == '初' || prev == '後' || prev == '時') ? "份" : "分";
            case '量': return (prev == '思' || prev == '稱' || prev == '可' || prev == '取') ? "梁" : "亮";
                                                          // 思量、稱量、不可量 liáng；無量 liàng
            case '應': return next == '正' ? "硬" : "英";   // 應正等覺＝應供 yìng                       // 應如是、應生瞋恨、應供養 皆 yīng（TTS 默認 yìng）
            case '為':
                // 「名為…」是判斷（wéi），優先於後面的字
                if (prev == '名') return "唯";
                // wèi（替、為著）
                if (next == '人' || next == '他' || next == '汝' || next == '利'
                        || next == '歌' || next == '欲' || next == '諸') return "位";
                if (next == '發' && prev != '名') return "位";   // 為發大乘者說、普為發趣…者
                // wéi（是、就是、做）：即為非住、寧為多不、無為法、為世間…之所供養（被動）
                return "唯";
            case '切': return "窃";                       // 一切 qiè（TTS 默認 qiē）
            case '相': return "象";                       // 我相、人相 xiàng（TTS 默認 xiāng）
            default: return String.valueOf(ch);
        }
    }
}
