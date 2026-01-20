package be.transcode.morningdeck.server.core.model;

public enum Language {
    AFRIKAANS("af", "Afrikaans"),
    ARABIC("ar", "Arabic"),
    ARMENIAN("hy", "Armenian"),
    AZERBAIJANI("az", "Azerbaijani"),
    BELARUSIAN("be", "Belarusian"),
    BOSNIAN("bs", "Bosnian"),
    BULGARIAN("bg", "Bulgarian"),
    CATALAN("ca", "Catalan"),
    CHINESE_SIMPLIFIED("zh", "Chinese (Simplified)"),
    CROATIAN("hr", "Croatian"),
    CZECH("cs", "Czech"),
    DANISH("da", "Danish"),
    DUTCH("nl", "Dutch"),
    ENGLISH("en", "English"),
    ESTONIAN("et", "Estonian"),
    FINNISH("fi", "Finnish"),
    FRENCH("fr", "French"),
    GALICIAN("gl", "Galician"),
    GERMAN("de", "German"),
    GREEK("el", "Greek"),
    HEBREW("he", "Hebrew"),
    HINDI("hi", "Hindi"),
    HUNGARIAN("hu", "Hungarian"),
    ICELANDIC("is", "Icelandic"),
    INDONESIAN("id", "Indonesian"),
    ITALIAN("it", "Italian"),
    JAPANESE("ja", "Japanese"),
    KANNADA("kn", "Kannada"),
    KAZAKH("kk", "Kazakh"),
    KOREAN("ko", "Korean"),
    LATVIAN("lv", "Latvian"),
    LITHUANIAN("lt", "Lithuanian"),
    MACEDONIAN("mk", "Macedonian"),
    MALAY("ms", "Malay"),
    MARATHI("mr", "Marathi"),
    MAORI("mi", "Māori"),
    NEPALI("ne", "Nepali"),
    NORWEGIAN_BOKMAL("nb", "Norwegian Bokmål"),
    PERSIAN("fa", "Persian"),
    POLISH("pl", "Polish"),
    PORTUGUESE("pt", "Portuguese"),
    ROMANIAN("ro", "Romanian"),
    RUSSIAN("ru", "Russian"),
    SERBIAN("sr", "Serbian"),
    SLOVAK("sk", "Slovak"),
    SLOVENIAN("sl", "Slovenian"),
    SPANISH("es", "Spanish"),
    SWAHILI("sw", "Swahili"),
    SWEDISH("sv", "Swedish"),
    FILIPINO("fil", "Filipino"),
    TAMIL("ta", "Tamil"),
    THAI("th", "Thai"),
    TURKISH("tr", "Turkish"),
    UKRAINIAN("uk", "Ukrainian"),
    URDU("ur", "Urdu"),
    VIETNAMESE("vi", "Vietnamese"),
    WELSH("cy", "Welsh");

    private final String languageCode;
    private final String displayName;

    Language(String languageCode, String displayName) {
        this.languageCode = languageCode;
        this.displayName = displayName;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static Language fromCode(String code) {
        for (Language language : values()) {
            if (language.getLanguageCode().equalsIgnoreCase(code)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unknown language code: " + code);
    }
}
