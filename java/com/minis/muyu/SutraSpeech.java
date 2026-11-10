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
            case '舍': return next == '衛' ? "设" : "舍";  // shè
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
            case '缽': return "波";                       // bō
            case '諦': return "弟";                       // dì
            case '訖': return "气";                       // qì
            case '敷': return "夫";                       // fū
            case '袒': return "毯";                       // tǎn
            case '膝': return "西";                       // xī
            case '囑': return "主";                       // zhǔ
            case '筏': return "罚";                       // fá
            case '切': return "窃";                       // 一切 qiè（TTS 默認 qiē）
            case '相': return "象";                       // 我相、人相 xiàng（TTS 默認 xiāng）
            default: return String.valueOf(ch);
        }
    }
}
