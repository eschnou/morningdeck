package be.transcode.morningdeck.server.provider.ai;

/**
 * Feature keys for AI service operations.
 * Used to attribute API usage to specific features and determine model tier.
 */
public enum AiFeature {
    ENRICH(ModelTier.LITE),
    SCORE(ModelTier.LITE),
    ENRICH_SCORE(ModelTier.LITE),
    EMAIL_EXTRACT(ModelTier.LITE),
    WEB_EXTRACT(ModelTier.LITE),
    REPORT_GEN(ModelTier.LITE);

    private final ModelTier tier;

    AiFeature(ModelTier tier) {
        this.tier = tier;
    }

    public ModelTier getTier() {
        return tier;
    }

    /**
     * Model tier determines which AI model to use.
     * LITE: Fast, cost-effective model for simple tasks.
     * HEAVY: More capable model for complex generation tasks.
     */
    public enum ModelTier {
        LITE,
        HEAVY
    }
}
