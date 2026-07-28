package web.tosunsaeng.global.config.auth;

public enum AuthMode {

    LEGACY("legacy"),
    JWT("jwt");

    static final String SUPPORTED_VALUES_MESSAGE =
            "Authentication mode must be one of the supported values: legacy, jwt";

    private final String propertyValue;

    AuthMode(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String getPropertyValue() {
        return propertyValue;
    }

    public static AuthMode fromProperty(String value) {
        for (AuthMode mode : values()) {
            if (mode.propertyValue.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException(SUPPORTED_VALUES_MESSAGE);
    }
}
